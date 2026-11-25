package com.certifika2.usuario.model;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity @Table(name="postulaciones_profesor") @Getter @Setter
public class Postulacion {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private String id;
    @Column(nullable=false, unique=true) private String applicantId;
    @Column(nullable=false) private String cognitoUsername;
    @Column(nullable=false) private String applicantName;
    @Column(nullable=false) private String applicantEmail;
    @Column(nullable=false,length=80) private String specialty;
    private int experienceYears;
    @Column(length=500) private String profileUrl;
    @Column(length=600) private String motivation;
    @Column(length=80) private String availability;
    @Enumerated(EnumType.STRING) @Column(nullable=false) private Estado status = Estado.PENDING;
    private Instant createdAt = Instant.now();
    private Instant reviewedAt;
    private String reviewedBy;
    @Version private Long version;
    public enum Estado { PENDING, APPROVED, REJECTED }
}
