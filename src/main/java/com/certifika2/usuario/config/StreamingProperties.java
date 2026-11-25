package com.certifika2.usuario.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "certifika2")
public record StreamingProperties(Kinesis kinesis, Firehose firehose, S3 s3) {

    public record Kinesis(String streamSolicitudes, String streamDlq, Consumer consumer) {
    }

    public record Consumer(boolean enabled, String nombre, long pollIntervalMs,
                           int maxRecords, int maxIntentos, long backoffInicialMs) {
    }

    public record Firehose(String streamEmitidos) {
    }

    public record S3(String bucketCertificados) {
    }
}
