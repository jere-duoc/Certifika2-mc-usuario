package com.certifika2.usuario.messaging;

import com.certifika2.usuario.config.StreamingProperties;
import com.certifika2.usuario.messaging.dto.EventoFallido;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kinesis.KinesisClient;
import software.amazon.awssdk.services.kinesis.model.PutRecordRequest;

/** Destino final de los eventos que no se pudieron procesar (Dead Letter Queue). */
@Slf4j
@Component
@RequiredArgsConstructor
public class KinesisDlqPublisher {

    private final KinesisClient kinesisClient;
    private final StreamingProperties props;
    private final ObjectMapper objectMapper;

    /** @return true si el evento quedó guardado en la DLQ. */
    public boolean enviar(EventoFallido fallido) {
        try {
            String partitionKey = fallido.eventIdOriginal() != null ? fallido.eventIdOriginal() : fallido.sequenceNumber();
            kinesisClient.putRecord(PutRecordRequest.builder()
                    .streamName(props.kinesis().streamDlq())
                    .partitionKey(partitionKey)
                    .data(SdkBytes.fromUtf8String(objectMapper.writeValueAsString(fallido)))
                    .build());
            log.error("Evento enviado a DLQ {} tipo={} eventId={} intentos={} error={}",
                    props.kinesis().streamDlq(), fallido.tipoFalla(), fallido.eventIdOriginal(),
                    fallido.intentos(), fallido.error());
            return true;
        } catch (Exception e) {
            log.error("No se pudo escribir en la DLQ el evento {} (seq {}): {}",
                    fallido.eventIdOriginal(), fallido.sequenceNumber(), e.getMessage());
            return false;
        }
    }
}
