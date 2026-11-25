package com.certifika2.usuario.service;

import com.certifika2.usuario.config.StreamingProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/** Guarda y lee los archivos de certificados en S3. */
@Component
@RequiredArgsConstructor
public class CertificadoStorage {

    private final S3Client s3Client;
    private final StreamingProperties props;

    public String bucket() {
        return props.s3().bucketCertificados();
    }

    public void subir(String key, byte[] contenido, String contentType) {
        s3Client.putObject(PutObjectRequest.builder()
                        .bucket(bucket())
                        .key(key)
                        .contentType(contentType)
                        .build(),
                RequestBody.fromBytes(contenido));
    }

    public byte[] descargar(String bucket, String key) {
        return s3Client.getObjectAsBytes(GetObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .build())
                .asByteArray();
    }
}
