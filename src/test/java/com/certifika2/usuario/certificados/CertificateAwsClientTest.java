package com.certifika2.usuario.certificados;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;

class CertificateAwsClientTest {
    @Test void kinesisWireRequestIsSignedAndContainsEncodedEvent() throws Exception {
        var target = new AtomicReference<String>(); var authorization = new AtomicReference<String>();
        var body = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            target.set(exchange.getRequestHeaders().getFirst("X-Amz-Target"));
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "{\"SequenceNumber\":\"123\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length); exchange.getResponseBody().write(response); exchange.close();
        });
        server.start();
        try {
            var mapper = new ObjectMapper(); var client = client(server, mapper);
            assertThat(client.kinesis("PutRecord", Map.of("StreamName", "stream", "PartitionKey", "student", "Data", "e30="))
                    .path("SequenceNumber").asText()).isEqualTo("123");
            assertThat(target.get()).isEqualTo("Kinesis_20131202.PutRecord");
            assertThat(authorization.get()).startsWith("AWS4-HMAC-SHA256 ").contains("/us-east-1/kinesis/aws4_request");
            assertThat(mapper.readTree(body.get()).path("Data").asText()).isEqualTo("e30=");
            client.close();
        } finally { server.stop(0); }
    }
    @Test void s3DownloadPreservesBinaryBytesAndServiceErrorsDoNotExposeBody() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        byte[] expected = {0, 1, (byte)255, (byte)128, 10};
        server.createContext("/test-bucket/certificados/one.pdf", exchange -> {
            exchange.sendResponseHeaders(200, expected.length); exchange.getResponseBody().write(expected); exchange.close();
        });
        server.createContext("/", exchange -> {
            byte[] data = "private-service-response".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(503, data.length); exchange.getResponseBody().write(data); exchange.close();
        });
        server.start();
        try {
            var client = client(server, new ObjectMapper());
            assertThat(client.getObject("test-bucket", "certificados/one.pdf")).isEqualTo(expected);
            assertThatThrownBy(() -> client.getObject("test-bucket", "missing.pdf"))
                    .hasMessage("s3 HTTP 503").hasMessageNotContaining("private-service-response");
            client.close();
        } finally { server.stop(0); }
    }
    @Test void firehoseWireRequestIsSignedAndContainsJsonLine() throws Exception {
        var target = new AtomicReference<String>(); var authorization = new AtomicReference<String>();
        var body = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            target.set(exchange.getRequestHeaders().getFirst("X-Amz-Target"));
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "{\"RecordId\":\"fh-123\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length); exchange.getResponseBody().write(response); exchange.close();
        });
        server.start();
        try {
            var mapper = new ObjectMapper();
            String endpoint = "http://127.0.0.1:" + server.getAddress().getPort();
            var client = new CertificateAwsClient(mapper, "us-east-1", endpoint, endpoint, endpoint,
                    StaticCredentialsProvider.create(AwsBasicCredentials.create("unit-test", "unit-test-secret")));
            assertThat(client.firehosePutRecord("certifika2-emitidos", "{\"ok\":true}\n".getBytes(StandardCharsets.UTF_8))
                    .path("RecordId").asText()).isEqualTo("fh-123");
            assertThat(target.get()).isEqualTo("Firehose_20150804.PutRecord");
            assertThat(authorization.get()).startsWith("AWS4-HMAC-SHA256 ").contains("/us-east-1/firehose/aws4_request");
            assertThat(mapper.readTree(body.get()).path("DeliveryStreamName").asText()).isEqualTo("certifika2-emitidos");
            client.close();
        } finally { server.stop(0); }
    }
    private CertificateAwsClient client(HttpServer server, ObjectMapper mapper) {
        String endpoint = "http://127.0.0.1:" + server.getAddress().getPort();
        return new CertificateAwsClient(mapper, "us-east-1", endpoint, endpoint,
                StaticCredentialsProvider.create(AwsBasicCredentials.create("unit-test", "unit-test-secret")));
    }
}
