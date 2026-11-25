package com.certifika2.usuario.messaging.dto;

import java.time.Instant;

/**
 * Registro analítico enviado a Kinesis Data Firehose cuando un certificado queda emitido.
 * Firehose lo acumula y lo entrega como JSON por líneas en S3 (prefijo emitidos/),
 * listo para consultarse con Athena o cargarse en un data warehouse.
 */
public record CertificadoEmitidoEvent(
        String eventType,
        String eventIdOrigen,
        String codigoVerificacion,
        Long idCertificado,
        String idUsuario,
        Long idCurso,
        String codigoCurso,
        String nombreCurso,
        String nivelCurso,
        String formato,
        Long tamanoBytes,
        Instant fechaCompletado,
        Instant fechaEmision,
        long latenciaMs
) {
    public static final String TYPE = "CERTIFICADO_EMITIDO";
}
