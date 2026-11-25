package com.certifika2.usuario.repository;

import com.certifika2.usuario.model.KinesisCheckpoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface KinesisCheckpointRepository extends JpaRepository<KinesisCheckpoint, String> {
}
