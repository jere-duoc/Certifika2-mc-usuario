package com.certifika2.usuario.messaging.dto;

import java.time.Instant;

/** Mensaje que se escribe en el stream DLQ cuando un evento no se pudo procesar. */
public record EventoFallido(
        String eventIdOriginal,
        String tipoFalla,          // NO_RECUPERABLE | REINTENTOS_AGOTADOS | JSON_INVALIDO
        String error,
        int intentos,
        String streamOrigen,
        String shardId,
        String sequenceNumber,
        String payloadOriginal,
        Instant fechaFalla
) {
}
