package com.certifika2.usuario.certificados;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
@Entity @Getter @Setter @Table(name = "certificado_stream_checkpoint")
public class StreamCheckpoint {
    @Id @Column(length = 256) private String id;
    @Column(length = 129) private String secuencia;
    private Instant actualizadoEn;
}
