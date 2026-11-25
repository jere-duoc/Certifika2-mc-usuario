package com.certifika2.usuario.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.firehose.FirehoseClient;
import software.amazon.awssdk.services.firehose.FirehoseClientBuilder;
import software.amazon.awssdk.services.kinesis.KinesisClient;
import software.amazon.awssdk.services.kinesis.KinesisClientBuilder;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;

@Configuration
public class AwsConfig {

    private final AwsProperties props;

    public AwsConfig(AwsProperties props) {
        this.props = props;
    }

    @Bean(destroyMethod = "close")
    public KinesisClient kinesisClient() {
        KinesisClientBuilder builder = KinesisClient.builder()
                .region(Region.of(props.region()))
                .credentialsProvider(credenciales());
        if (props.usaEndpointLocal()) {
            builder.endpointOverride(URI.create(props.endpoint()));
        }
        return builder.build();
    }

    @Bean(destroyMethod = "close")
    public FirehoseClient firehoseClient() {
        FirehoseClientBuilder builder = FirehoseClient.builder()
                .region(Region.of(props.region()))
                .credentialsProvider(credenciales());
        if (props.usaEndpointLocal()) {
            builder.endpointOverride(URI.create(props.endpoint()));
        }
        return builder.build();
    }

    @Bean(destroyMethod = "close")
    public S3Client s3Client() {
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(props.region()))
                .credentialsProvider(credenciales());
        if (props.usaEndpointLocal()) {
            builder.endpointOverride(URI.create(props.endpoint()))
                   // LocalStack necesita path-style (http://localhost:4566/bucket/key)
                   .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build());
        }
        return builder.build();
    }

    private AwsCredentialsProvider credenciales() {
        if (props.usaEndpointLocal()) {
            return StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test"));
        }
        return DefaultCredentialsProvider.create();
    }
}
