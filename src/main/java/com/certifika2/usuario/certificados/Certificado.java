package com.certifika2.usuario.certificados;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity @Getter @Setter
@Table(name = "certificados", uniqueConstraints = @UniqueConstraint(columnNames = {"idUsuario", "idCurso", "formato"}))
public class Certificado {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long idCertificado;
    @Column(nullable = false, unique = true, length = 36) private String eventId;
    @Column(nullable = false, unique = true, length = 36) private String codigoVerificacion;
    @Column(nullable = false, length = 128) private String idUsuario;
    @Column(nullable = false) private String nombreTitular;
    @Column(nullable = false) private Long idCurso;
    private String codigoCurso;
    @Column(nullable = false) private String nombreCurso;
    private String nivelCurso;
    @Column(nullable = false, length = 3) private String formato;
    private long tamanoBytes;
    private Instant fechaCompletado;
    private Instant fechaEmision;
    @Column(nullable = false) private String bucket;
    @Column(nullable = false) private String objectKey;
}
