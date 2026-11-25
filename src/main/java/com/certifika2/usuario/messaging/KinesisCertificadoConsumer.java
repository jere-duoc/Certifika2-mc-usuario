package com.certifika2.usuario.messaging;

import com.certifika2.usuario.config.StreamingProperties;
import com.certifika2.usuario.exception.EventoNoRecuperableException;
import com.certifika2.usuario.messaging.dto.CertificadoSolicitadoEvent;
import com.certifika2.usuario.messaging.dto.EventoFallido;
import com.certifika2.usuario.model.KinesisCheckpoint;
import com.certifika2.usuario.repository.KinesisCheckpointRepository;
import com.certifika2.usuario.service.CertificadoService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.kinesis.KinesisClient;
import software.amazon.awssdk.services.kinesis.model.GetRecordsRequest;
import software.amazon.awssdk.services.kinesis.model.GetRecordsResponse;
import software.amazon.awssdk.services.kinesis.model.GetShardIteratorRequest;
import software.amazon.awssdk.services.kinesis.model.ListShardsRequest;
import software.amazon.awssdk.services.kinesis.model.Record;
import software.amazon.awssdk.services.kinesis.model.Shard;
import software.amazon.awssdk.services.kinesis.model.ShardIteratorType;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Consumidor del stream de solicitudes de certificados.
 * <p>
 * Solo contiene infraestructura de mensajería: lee registros por shard, los deserializa,
 * aplica reintentos y DLQ, y guarda el checkpoint. La lógica de negocio está en
 * {@link CertificadoService}.
 * <p>
 * Semántica: al-menos-una-vez. Se hace checkpoint después de procesar cada registro;
 * si el servicio se cae antes, el registro se relee y la idempotencia por eventId evita
 * certificados duplicados.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "certifika2.kinesis.consumer", name = "enabled", havingValue = "true", matchIfMissing = true)
public class KinesisCertificadoConsumer {

    private final KinesisClient kinesisClient;
    private final KinesisCheckpointRepository checkpointRepository;
    private final CertificadoService certificadoService;
    private final KinesisDlqPublisher dlqPublisher;
    private final ObjectMapper objectMapper;
    private final StreamingProperties.Kinesis kinesisProps;
    private final StreamingProperties.Consumer consumerProps;

    public KinesisCertificadoConsumer(KinesisClient kinesisClient,
                                      KinesisCheckpointRepository checkpointRepository,
                                      CertificadoService certificadoService,
                                      KinesisDlqPublisher dlqPublisher,
                                      ObjectMapper objectMapper,
                                      StreamingProperties props) {
        this.kinesisClient = kinesisClient;
        this.checkpointRepository = checkpointRepository;
        this.certificadoService = certificadoService;
        this.dlqPublisher = dlqPublisher;
        this.objectMapper = objectMapper;
        this.kinesisProps = props.kinesis();
        this.consumerProps = props.kinesis().consumer();
    }

    @Scheduled(initialDelay = 5000, fixedDelayString = "${certifika2.kinesis.consumer.poll-interval-ms:2000}")
    public void poll() {
        List<Shard> shards;
        try {
            shards = kinesisClient.listShards(ListShardsRequest.builder()
                    .streamName(kinesisProps.streamSolicitudes())
                    .build()).shards();
        } catch (SdkException e) {
            log.warn("No se pudo listar shards de {}: {}", kinesisProps.streamSolicitudes(), e.getMessage());
            return;
        }
        for (Shard shard : shards) {
            try {
                consumirShard(shard.shardId());
            } catch (SdkException e) {
                // Falla transitoria de Kinesis: se reintenta en el siguiente ciclo desde el último checkpoint.
                log.warn("Error leyendo shard {}: {}", shard.shardId(), e.getMessage());
            }
        }
    }

    private void consumirShard(String shardId) {
        String checkpointId = KinesisCheckpoint.idDe(consumerProps.nombre(), kinesisProps.streamSolicitudes(), shardId);
        Optional<KinesisCheckpoint> checkpoint = checkpointRepository.findById(checkpointId);

        GetShardIteratorRequest.Builder iteradorReq = GetShardIteratorRequest.builder()
                .streamName(kinesisProps.streamSolicitudes())
                .shardId(shardId);
        if (checkpoint.isPresent() && checkpoint.get().getSequenceNumber() != null) {
            iteradorReq.shardIteratorType(ShardIteratorType.AFTER_SEQUENCE_NUMBER)
                       .startingSequenceNumber(checkpoint.get().getSequenceNumber());
        } else {
            // Primera vez: desde el registro más antiguo retenido para no perder solicitudes.
            iteradorReq.shardIteratorType(ShardIteratorType.TRIM_HORIZON);
        }
        String iterador = kinesisClient.getShardIterator(iteradorReq.build()).shardIterator();

        GetRecordsResponse respuesta = kinesisClient.getRecords(GetRecordsRequest.builder()
                .shardIterator(iterador)
                .limit(consumerProps.maxRecords())
                .build());

        if (!respuesta.records().isEmpty()) {
            log.info("Shard {}: {} registros (atraso {} ms)", shardId,
                    respuesta.records().size(), respuesta.millisBehindLatest());
        }

        for (Record registro : respuesta.records()) {
            if (!procesarRegistro(shardId, registro)) {
                // No se pudo procesar ni enviar a DLQ: se detiene el shard sin avanzar el checkpoint
                // para no perder el evento ni romper el orden del usuario.
                return;
            }
            guardarCheckpoint(checkpointId, shardId, registro.sequenceNumber());
        }
    }

    /** @return true si el registro quedó resuelto (procesado, duplicado o en DLQ). */
    private boolean procesarRegistro(String shardId, Record registro) {
        String payload = registro.data().asUtf8String();
        CertificadoSolicitadoEvent evento;
        try {
            evento = objectMapper.readValue(payload, CertificadoSolicitadoEvent.class);
        } catch (Exception e) {
            // Mensaje mal formado: falla definitiva, reintentar no ayuda.
            return aDlq(null, "JSON_INVALIDO", e, 1, shardId, registro, payload);
        }

        int maxIntentos = Math.max(1, consumerProps.maxIntentos());
        for (int intento = 1; ; intento++) {
            try {
                certificadoService.procesarSolicitud(evento);
                return true;
            } catch (EventoNoRecuperableException e) {
                return aDlq(evento.eventId(), "NO_RECUPERABLE", e, intento, shardId, registro, payload);
            } catch (DataIntegrityViolationException e) {
                // Carrera con otra instancia que guardó el mismo certificado: ya está emitido.
                log.info("Evento {} ya emitido por otro proceso (restricción única), se omite", evento.eventId());
                return true;
            } catch (Exception e) {
                // Falla transitoria (S3, base de datos, red): reintento con backoff exponencial.
                if (intento >= maxIntentos) {
                    return aDlq(evento.eventId(), "REINTENTOS_AGOTADOS", e, intento, shardId, registro, payload);
                }
                long espera = consumerProps.backoffInicialMs() * (1L << (intento - 1));
                log.warn("Evento {} falló (intento {}/{}): {}. Reintento en {} ms",
                        evento.eventId(), intento, maxIntentos, e.getMessage(), espera);
                if (!dormir(espera)) {
                    return false;
                }
            }
        }
    }

    private boolean aDlq(String eventId, String tipo, Exception e, int intentos,
                         String shardId, Record registro, String payload) {
        return dlqPublisher.enviar(new EventoFallido(
                eventId, tipo, e.getClass().getSimpleName() + ": " + e.getMessage(), intentos,
                kinesisProps.streamSolicitudes(), shardId, registro.sequenceNumber(), payload, Instant.now()));
    }

    private void guardarCheckpoint(String id, String shardId, String sequenceNumber) {
        KinesisCheckpoint cp = checkpointRepository.findById(id).orElseGet(() -> {
            KinesisCheckpoint nuevo = new KinesisCheckpoint();
            nuevo.setId(id);
            nuevo.setConsumidor(consumerProps.nombre());
            nuevo.setStream(kinesisProps.streamSolicitudes());
            nuevo.setShardId(shardId);
            return nuevo;
        });
        cp.setSequenceNumber(sequenceNumber);
        cp.setActualizado(Instant.now());
        checkpointRepository.save(cp);
    }

    private boolean dormir(long ms) {
        try {
            Thread.sleep(ms);
            return true;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
