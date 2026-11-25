package com.certifika2.usuario.certificados;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;
public interface StreamCheckpointRepository extends JpaRepository<StreamCheckpoint, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select c from StreamCheckpoint c where c.id = :id")
    Optional<StreamCheckpoint> bloquear(@Param("id") String id);
}
