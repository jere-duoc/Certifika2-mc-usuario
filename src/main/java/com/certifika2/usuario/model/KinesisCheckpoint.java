package com.certifika2.usuario.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Última posición procesada por el consumidor en cada shard.
 * Permite que, tras un reinicio, el consumo continúe desde donde quedó
 * (equivalente a los offsets confirmados de un consumer group).
 */
@Entity
@Table(name = "kinesis_checkpoints")
@Data
@NoArgsConstructor
public class KinesisCheckpoint {

    /** consumidor + stream + shard */
    @Id
    @Column(length = 255)
    private String id;

    private String consumidor;
    private String stream;
    private String shardId;

    @Column(length = 128)
    private String sequenceNumber;

    private Instant actualizado;

    public static String idDe(String consumidor, String stream, String shardId) {
        return consumidor + ":" + stream + ":" + shardId;
    }
}
