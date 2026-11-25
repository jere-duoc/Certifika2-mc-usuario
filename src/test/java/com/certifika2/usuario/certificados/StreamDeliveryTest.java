package com.certifika2.usuario.certificados;
import com.certifika2.usuario.model.Usuario;
import com.certifika2.usuario.repository.UsuarioRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import java.time.Instant;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

@DataJpaTest @Import({CertificadoProcessor.class, CertificadoRenderer.class, StreamBatchProcessor.class, StreamDeliveryTest.JsonConfig.class})
@TestPropertySource(properties = {
        "app.certificados.bucket=certifika2-certificados-test",
        "app.certificados.firehose-stream=certifika2-emitidos-test"
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class StreamDeliveryTest {
    @Autowired StreamBatchProcessor batches;
    @Autowired CertificadoRepository certificados;
    @Autowired StreamCheckpointRepository checkpoints;
    @Autowired StreamFailureRepository failures;
    @Autowired UsuarioRepository usuarios;
    @Autowired ObjectMapper mapper;
    @MockBean CertificateAwsClient aws;
    @BeforeEach void setup() {
        certificados.deleteAll(); checkpoints.deleteAll(); failures.deleteAll(); usuarios.deleteAll();
        var u = new Usuario(); u.setIdExterna("student"); u.setEmail("student@test.invalid"); u.setNombreUsuario("María Figueroa"); usuarios.save(u);
    }
    @Test void replayDoesNotGenerateSecondCertificateOrUpload() throws Exception {
        var record = record("1", "PDF");
        batches.procesar("stream:shard", List.of(record));
        batches.procesar("stream:shard", List.of(record));
        // A second sequence can contain the same event after a producer retry.
        var retry = record.deepCopy(); retry.put("SequenceNumber", "2");
        batches.procesar("stream:shard", List.of(retry));
        assertThat(certificados.count()).isEqualTo(1);
        assertThat(certificados.findAll().get(0).getNombreTitular()).isEqualTo("María Figueroa");
        assertThat(checkpoints.findById("stream:shard").orElseThrow().getSecuencia()).isEqualTo("2");
        verify(aws, times(1)).putObject(anyString(), anyString(), any(), eq("application/pdf"));
    }
    @Test void failedUploadDoesNotAdvanceCheckpointOrPublishCertificate() throws Exception {
        doThrow(new IllegalStateException("S3 unavailable")).when(aws).putObject(anyString(), anyString(), any(), anyString());
        assertThatThrownBy(() -> batches.procesar("stream:shard", List.of(record("1", "PDF"))))
                .isInstanceOf(StreamBatchProcessor.RegistroFallido.class);
        assertThat(certificados.count()).isZero(); assertThat(checkpoints.findById("stream:shard")).isEmpty();
        reset(aws);
        batches.procesar("stream:shard", List.of(record("1", "PDF")));
        assertThat(certificados.count()).isEqualTo(1);
    }
    @Test void malformedRecordDoesNotErasePreviouslyCommittedDelivery() throws Exception {
        batches.procesar("stream:shard", List.of(record("1", "PDF")));
        var bad = mapper.createObjectNode().put("SequenceNumber", "2").put("Data", "not-base64!");
        assertThatThrownBy(() -> batches.procesar("stream:shard", List.of(bad))).isInstanceOf(StreamBatchProcessor.RegistroFallido.class);
        assertThat(certificados.count()).isEqualTo(1);
        assertThat(checkpoints.findById("stream:shard").orElseThrow().getSecuencia()).isEqualTo("1");
    }
    @Test void actualPngIsStoredForPngRequest() throws Exception {
        batches.procesar("stream:shard", List.of(record("1", "PNG")));
        var data = org.mockito.ArgumentCaptor.forClass(byte[].class);
        verify(aws).putObject(anyString(), anyString(), data.capture(), eq("image/png"));
        var image = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(data.getValue()));
        assertThat(image.getWidth()).isEqualTo(1600); assertThat(image.getHeight()).isEqualTo(1120);
        verify(aws, times(1)).firehosePutRecord(eq("certifika2-emitidos-test"), any());
    }
    @Test void firehoseFailureDoesNotBlockCertificateIssuance() throws Exception {
        doThrow(new IllegalStateException("Firehose unavailable")).when(aws).firehosePutRecord(anyString(), any());
        batches.procesar("stream:shard", List.of(record("1", "PDF")));
        assertThat(certificados.count()).isEqualTo(1);
        assertThat(checkpoints.findById("stream:shard").orElseThrow().getSecuencia()).isEqualTo("1");
    }
    private com.fasterxml.jackson.databind.node.ObjectNode record(String sequence, String format) throws Exception {
        var event = new CertificadoProcessor.Evento(1, "b60b0798-d293-4dda-bb0c-9aa69d5c068e", "student", 12L,
                "streaming", "Streaming de datos", "Avanzado", format, Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-02T00:00:00Z"));
        return mapper.createObjectNode().put("SequenceNumber", sequence)
                .put("Data", Base64.getEncoder().encodeToString(mapper.writeValueAsBytes(event)));
    }
    @TestConfiguration static class JsonConfig { @Bean ObjectMapper mapper() { return new ObjectMapper().findAndRegisterModules(); } }
}
