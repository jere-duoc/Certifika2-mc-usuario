package com.certifika2.usuario.certificados;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import java.time.Instant;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
@Service @RequiredArgsConstructor
public class StreamFailureService {
    private final StreamFailureRepository repository;
    public static String id(String shard, String secuencia) {
        return UUID.nameUUIDFromBytes((shard + ":" + secuencia).getBytes(StandardCharsets.UTF_8)).toString();
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrar(String shard, String secuencia, RuntimeException ex) {
        String id = id(shard, secuencia);
        var failure = repository.findById(id).orElseGet(StreamFailure::new);
        failure.setId(id); failure.setShard(shard); failure.setSecuencia(secuencia);
        failure.setError(ex.getClass().getSimpleName()); failure.setIntentos(failure.getIntentos() + 1);
        failure.setUltimoIntento(Instant.now()); failure.setResuelto(false); repository.save(failure);
    }
}
