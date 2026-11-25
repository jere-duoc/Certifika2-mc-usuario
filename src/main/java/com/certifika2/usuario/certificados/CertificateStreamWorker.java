package com.certifika2.usuario.certificados;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;
import java.util.*;

@Service @Slf4j @ConditionalOnProperty(name = "app.certificados.enabled", havingValue = "true")
public class CertificateStreamWorker {
    private final CertificateAwsClient aws;
    private final StreamCheckpointRepository checkpoints;
    private final StreamBatchProcessor batches;
    private final StreamFailureService failures;
    private final String stream;
    private final Map<String, String> iterators = new LinkedHashMap<>();
    private long rediscoverAt;
    public CertificateStreamWorker(CertificateAwsClient aws, StreamCheckpointRepository checkpoints,
            StreamBatchProcessor batches, StreamFailureService failures, @Value("${app.certificados.stream}") String stream,
            @Value("${app.certificados.bucket}") String bucket) {
        this.aws = aws; this.checkpoints = checkpoints; this.batches = batches; this.failures = failures; this.stream = stream;
        if (stream.isBlank() || bucket.isBlank()) throw new IllegalArgumentException("Stream y bucket obligatorios");
    }
    @Scheduled(fixedDelayString = "${app.certificados.poll-delay-ms:1500}")
    public void poll() {
        try {
            if (System.currentTimeMillis() >= rediscoverAt) {
                String next = null;
                do {
                    var result = aws.kinesis("ListShards", next == null ? Map.of("StreamName", stream) : Map.of("NextToken", next));
                    for (JsonNode shard : result.path("Shards")) iterators.putIfAbsent(shard.path("ShardId").asText(), "");
                    next = result.path("NextToken").asText(null);
                } while (next != null);
                rediscoverAt = System.currentTimeMillis() + 30000;
            }
            for (String shard : new ArrayList<>(iterators.keySet())) readShard(shard);
        } catch (RuntimeException ex) { log.warn("Consumidor Kinesis no disponible ({})", ex.getClass().getSimpleName()); }
    }
    private void readShard(String shard) {
        String key = stream + ":" + shard;
        try {
            String iterator = iterators.get(shard);
            if (iterator == null || iterator.isBlank()) {
                var checkpoint = checkpoints.findById(key);
                var args = new HashMap<String, Object>(); args.put("StreamName", stream); args.put("ShardId", shard);
                String sequence = checkpoint.map(StreamCheckpoint::getSecuencia).orElse(null);
                args.put("ShardIteratorType", sequence == null ? "TRIM_HORIZON" : "AFTER_SEQUENCE_NUMBER");
                if (sequence != null) args.put("StartingSequenceNumber", sequence);
                iterator = aws.kinesis("GetShardIterator", args).path("ShardIterator").asText();
            }
            var response = aws.kinesis("GetRecords", Map.of("ShardIterator", iterator, "Limit", 50));
            var records = response.path("Records");
            if (!records.isArray()) throw new IllegalStateException("Respuesta de registros invalida");
            // Commit each certificate and checkpoint together. A later bad record cannot roll back earlier deliveries.
            for (JsonNode record : records) batches.procesar(key, List.of(record));
            String next = response.path("NextShardIterator").asText(null);
            if (next == null) iterators.remove(shard); else iterators.put(shard, next);
        } catch (StreamBatchProcessor.RegistroFallido ex) {
            iterators.put(shard, "");
            failures.registrar(key, ex.sequence, (RuntimeException) ex.getCause());
            log.warn("Registro Kinesis pendiente en shard {} secuencia {}", shard, ex.sequence);
        } catch (RuntimeException ex) {
            iterators.put(shard, "");
            log.warn("No se avanzo el checkpoint de shard {} ({})", shard, ex.getClass().getSimpleName());
        }
    }
}
