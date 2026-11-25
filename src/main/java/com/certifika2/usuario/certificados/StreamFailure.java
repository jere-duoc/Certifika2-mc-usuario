package com.certifika2.usuario.certificados;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
@Entity @Getter @Setter @Table(name = "certificado_stream_failure")
public class StreamFailure {
    @Id @Column(length = 36) private String id;
    @Column(length = 256) private String shard;
    @Column(length = 129) private String secuencia;
    private String error;
    private int intentos;
    private Instant ultimoIntento;
    private boolean resuelto;
}
