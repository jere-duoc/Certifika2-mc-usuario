package com.certifika2.usuario.exception;

/**
 * Falla definitiva: reintentar no la arregla (datos inválidos, versión de contrato
 * no soportada). El evento va directo a la DLQ.
 */
public class EventoNoRecuperableException extends RuntimeException {
    public EventoNoRecuperableException(String message) {
        super(message);
    }
}
