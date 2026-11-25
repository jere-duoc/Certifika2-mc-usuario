package com.certifika2.usuario.controller.dto;

import com.certifika2.usuario.model.Certificado;

import java.time.Instant;

/** Respuesta pública: no expone email ni identificadores internos. */
public record VerificacionResponse(
        boolean valido,
        String codigoVerificacion,
        String nombreTitular,
        String nombreCurso,
        String nivelCurso,
        Instant fechaEmision
) {
    public static VerificacionResponse valido(Certificado c) {
        return new VerificacionResponse(true, c.getCodigoVerificacion(), c.getNombreTitular(),
                c.getNombreCurso(), c.getNivelCurso(), c.getFechaEmision());
    }

    public static VerificacionResponse invalido(String codigo) {
        return new VerificacionResponse(false, codigo, null, null, null, null);
    }
}
