package com.certifika2.usuario.controller.dto;

import com.certifika2.usuario.model.Certificado;

import java.time.Instant;

public record CertificadoResponse(
        Long idCertificado,
        String codigoVerificacion,
        String idUsuario,
        String nombreTitular,
        Long idCurso,
        String codigoCurso,
        String nombreCurso,
        String nivelCurso,
        String formato,
        Long tamanoBytes,
        Instant fechaCompletado,
        Instant fechaEmision
) {
    public static CertificadoResponse de(Certificado c) {
        return new CertificadoResponse(
                c.getIdCertificado(), c.getCodigoVerificacion(),
                c.getIdExternaUsuario(), c.getNombreTitular(),
                c.getIdCurso(), c.getCodigoCurso(), c.getNombreCurso(), c.getNivelCurso(),
                c.getFormato().name(), c.getTamanoBytes(), c.getFechaCompletado(), c.getFechaEmision());
    }
}
