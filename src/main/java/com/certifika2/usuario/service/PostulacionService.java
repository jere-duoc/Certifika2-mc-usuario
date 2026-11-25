package com.certifika2.usuario.service;
import com.certifika2.usuario.model.Postulacion;
import com.certifika2.usuario.model.Postulacion.Estado;
import com.certifika2.usuario.repository.PostulacionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.*;

@Service @RequiredArgsConstructor
public class PostulacionService {
    private final PostulacionRepository repository;
    private final CognitoDocenteService cognito;
    @Transactional
    public Vista enviar(Jwt jwt, Entrada e) {
        String specialty=texto(e.specialty(),1,80), motivation=texto(e.motivation(),40,600);
        String profile=texto(e.profileUrl(),1,500), availability=texto(e.availability(),1,80);
        if(e.experienceYears()==null || e.experienceYears()<0 || e.experienceYears()>60
                || !profile.matches("https?://[^\\s]+")) throw bad("Antecedentes inválidos");
        Postulacion p=repository.bloquearPorApplicantId(jwt.getSubject()).orElseGet(Postulacion::new);
        if(p.getStatus()==Estado.APPROVED) throw new ResponseStatusException(HttpStatus.CONFLICT,"Postulación ya aprobada");
        var identity=cognito.identidad(jwt);
        p.setApplicantId(jwt.getSubject()); p.setCognitoUsername(identity.username());
        p.setApplicantName(identity.name()); p.setApplicantEmail(identity.email());
        p.setSpecialty(specialty); p.setExperienceYears(e.experienceYears()); p.setProfileUrl(profile);
        p.setMotivation(motivation); p.setAvailability(availability); p.setStatus(Estado.PENDING);
        p.setReviewedAt(null); p.setReviewedBy(null);
        return Vista.de(repository.saveAndFlush(p));
    }
    @Transactional(readOnly=true) public Vista mia(String sub) {
        return repository.findByApplicantId(sub).map(Vista::de).orElse(null);
    }
    @Transactional(readOnly=true) public List<Vista> listar() {
        return repository.findAllByOrderByCreatedAtDesc().stream().map(Vista::de).toList();
    }
    @Transactional
    public Vista revisar(String id, Estado decision, String admin) {
        if(decision==null || decision==Estado.PENDING) throw bad("Decisión inválida");
        Postulacion p=repository.bloquear(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Postulación no encontrada"));
        if(p.getStatus()==decision) return Vista.de(p);
        if(p.getStatus()!=Estado.PENDING) throw new ResponseStatusException(HttpStatus.CONFLICT,"La solicitud ya fue revisada");
        // Cognito first: failure leaves PENDING; retrying AdminAddUserToGroup is idempotent.
        if(decision==Estado.APPROVED) cognito.habilitar(p.getCognitoUsername());
        p.setStatus(decision); p.setReviewedAt(Instant.now()); p.setReviewedBy(admin);
        repository.flush(); return Vista.de(p);
    }
    private static String texto(String t,int min,int max) {
        String v=t==null?"":t.trim(); if(v.length()<min || v.length()>max) throw bad("Completa los antecedentes requeridos"); return v;
    }
    private static ResponseStatusException bad(String text) { return new ResponseStatusException(HttpStatus.BAD_REQUEST,text); }
    public record Entrada(String specialty,Integer experienceYears,String profileUrl,String motivation,String availability) {}
    public record Vista(String id,String applicantId,String applicantName,String applicantEmail,String specialty,
            int experienceYears,String profileUrl,String motivation,String availability,Estado status,Instant createdAt,Instant reviewedAt) {
        static Vista de(Postulacion p) { return new Vista(p.getId(),p.getApplicantId(),p.getApplicantName(),p.getApplicantEmail(),p.getSpecialty(),
                p.getExperienceYears(),p.getProfileUrl(),p.getMotivation(),p.getAvailability(),p.getStatus(),p.getCreatedAt(),p.getReviewedAt()); }
    }
}
