package com.certifika2.usuario.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Certificado emitido a un usuario por completar un curso.
 * El archivo (PDF o PNG) se guarda en S3; aquí queda la referencia y los metadatos.
 * <p>
 * El vínculo con el usuario es el {@code sub} de Cognito ({@code idExternaUsuario}),
 * el mismo identificador que usa ms-cursos. Si el usuario ya está registrado en esta
 * base, además se enlaza la entidad {@link Usuario}.
 */
@Entity
@Table(name = "certificados",
        uniqueConstraints = {
            // Un certificado por usuario, curso y formato
            @UniqueConstraint(name = "uk_certificado_usuario_curso_formato",
                    columnNames = {"id_externa_usuario", "id_curso", "formato"}),
            // Idempotencia: el mismo evento nunca genera dos certificados
            @UniqueConstraint(name = "uk_certificado_event_id", columnNames = {"event_id"})
        },
        indexes = @Index(name = "idx_certificado_usuario", columnList = "id_externa_usuario"))
@Data
@NoArgsConstructor
public class Certificado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idCertificado;

    /** Código público para verificar la autenticidad del certificado. */
    @Column(nullable = false, unique = true, length = 36)
    private String codigoVerificacion;

    @Column(name = "event_id", nullable = false, length = 36)
    private String eventId;

    /** {@code sub} de Cognito del titular. */
    @Column(name = "id_externa_usuario", nullable = false, length = 64)
    private String idExternaUsuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Column(nullable = false)
    private String nombreTitular;

    @Column(name = "id_curso", nullable = false)
    private Long idCurso;

    @Column(length = 120)
    private String codigoCurso;

    @Column(nullable = false)
    private String nombreCurso;

    private String nivelCurso;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private FormatoCertificado formato;

    @Column(nullable = false)
    private String s3Bucket;

    @Column(nullable = false)
    private String s3Key;

    private Long tamanoBytes;

    private Instant fechaCompletado;

    @Column(nullable = false)
    private Instant fechaEmision;
}
