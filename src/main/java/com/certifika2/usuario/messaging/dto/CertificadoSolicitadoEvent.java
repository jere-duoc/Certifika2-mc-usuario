package com.certifika2.usuario.messaging.dto;

import com.certifika2.usuario.model.FormatoCertificado;

import java.time.Instant;

/**
 * Contrato del evento leído desde el stream "certificados-solicitados"
 * (mismo contrato que publica ms-cursos, versión 1.x).
 * {@code idUsuario} es el {@code sub} de Cognito.
 * Campos desconocidos se ignoran para tolerar versiones menores nuevas del productor.
 */
public record CertificadoSolicitadoEvent(
        String eventId,
        String eventType,
        String version,
        Instant occurredAt,
        String idUsuario,
        String emailUsuario,
        String nombreUsuario,
        Long idCurso,
        String codigoCurso,
        String nombreCurso,
        String nivelCurso,
        String idProfesor,
        String solicitadoPor,
        Instant fechaCompletado,
        FormatoCertificado formato
) {
    public static final String TYPE = "CERTIFICADO_SOLICITADO";
}
