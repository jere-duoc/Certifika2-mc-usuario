package com.certifika2.usuario.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Date;

@Entity
@Table(name = "usuario_curso")
@Data
@NoArgsConstructor
public class UsuarioCurso {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idUsuarioCurso;

    @Column(nullable = false)
    private Long idUsuario;

    @Column(nullable = false)
    private Long idCurso;

    @Temporal(TemporalType.TIMESTAMP)
    private Date fechaInscripcion;

    private String estado; // "ACTIVO", "COMPLETADO", "CANCELADO"

    @Enumerated(EnumType.STRING)
    private RolCurso rolCurso;
}

