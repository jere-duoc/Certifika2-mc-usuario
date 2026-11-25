package com.certifika2.usuario.certificados;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigInteger;
import java.time.Instant;
import java.util.Base64;

@Service @RequiredArgsConstructor
public class StreamBatchProcessor {
    private final StreamCheckpointRepository checkpoints;
    private final StreamFailureRepository failures;
    private final CertificadoProcessor processor;
    @Transactional
    public void procesar(String shard, Iterable<JsonNode> records) {
        var checkpoint = checkpoints.bloquear(shard).orElseGet(() -> {
            var c = new StreamCheckpoint(); c.setId(shard); return checkpoints.saveAndFlush(c);
        });
        for (JsonNode record : records) {
            String sequence = record.path("SequenceNumber").asText();
            try {
                if (checkpoint.getSecuencia() != null && new BigInteger(sequence).compareTo(new BigInteger(checkpoint.getSecuencia())) <= 0) continue;
                processor.procesar(Base64.getDecoder().decode(record.path("Data").asText()));
                checkpoint.setSecuencia(sequence); checkpoint.setActualizadoEn(Instant.now());
                failures.findById(StreamFailureService.id(shard, sequence)).ifPresent(f -> f.setResuelto(true));
            } catch (RuntimeException ex) { throw new RegistroFallido(sequence, ex); }
        }
    }
    public static class RegistroFallido extends RuntimeException {
        public final String sequence;
        public RegistroFallido(String sequence, RuntimeException cause) { super(cause); this.sequence = sequence; }
    }
}
