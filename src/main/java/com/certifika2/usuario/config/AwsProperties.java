package com.certifika2.usuario.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Conexión a AWS. Con {@code endpoint} se apunta a LocalStack; vacío = AWS real
 * usando la cadena de credenciales por defecto.
 */
@ConfigurationProperties(prefix = "certifika2.aws")
public record AwsProperties(String region, String endpoint) {

    public boolean usaEndpointLocal() {
        return endpoint != null && !endpoint.isBlank();
    }
}
