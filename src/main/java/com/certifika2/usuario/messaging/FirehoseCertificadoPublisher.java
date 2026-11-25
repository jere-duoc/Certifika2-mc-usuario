package com.certifika2.usuario.messaging;

import com.certifika2.usuario.config.StreamingProperties;
import com.certifika2.usuario.messaging.dto.CertificadoEmitidoEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.firehose.FirehoseClient;
import software.amazon.awssdk.services.firehose.model.PutRecordRequest;
import software.amazon.awssdk.services.firehose.model.Record;

/**
 * Envía a Kinesis Data Firehose (DirectPut) un registro por cada certificado emitido.
 * Es analítica: si falla se registra en el log, pero no se revierte la emisión del certificado.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FirehoseCertificadoPublisher {

    private final FirehoseClient firehoseClient;
    private final StreamingProperties props;
    private final ObjectMapper objectMapper;

    public void publicar(CertificadoEmitidoEvent evento) {
        try {
            // Salto de línea final: Firehose concatena registros; así S3 queda en formato JSON Lines.
            String linea = objectMapper.writeValueAsString(evento) + "\n";
            firehoseClient.putRecord(PutRecordRequest.builder()
                    .deliveryStreamName(props.firehose().streamEmitidos())
                    .record(Record.builder().data(SdkBytes.fromUtf8String(linea)).build())
                    .build());
            log.info("Certificado {} enviado a Firehose {}", evento.codigoVerificacion(), props.firehose().streamEmitidos());
        } catch (Exception e) {
            log.warn("No se pudo enviar a Firehose el certificado {}: {}", evento.codigoVerificacion(), e.getMessage());
        }
    }
}
