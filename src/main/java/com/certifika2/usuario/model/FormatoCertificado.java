package com.certifika2.usuario.model;

/** Tipo de archivo del certificado: documento PDF o imagen PNG. */
public enum FormatoCertificado {
    PDF("application/pdf", "pdf"),
    PNG("image/png", "png");

    private final String contentType;
    private final String extension;

    FormatoCertificado(String contentType, String extension) {
        this.contentType = contentType;
        this.extension = extension;
    }

    public String getContentType() {
        return contentType;
    }

    public String getExtension() {
        return extension;
    }
}
