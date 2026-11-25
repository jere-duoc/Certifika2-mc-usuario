package com.certifika2.usuario.certificados;

import com.certifika2.usuario.repository.UsuarioRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Service
public class CertificadoProcessor {
    private final CertificadoRepository certificados;
    private final UsuarioRepository usuarios;
    private final ObjectMapper mapper;
    private final CertificadoRenderer renderer;
    private final CertificateAwsClient aws;
    private final String bucket;
    public CertificadoProcessor(CertificadoRepository certificados, UsuarioRepository usuarios, ObjectMapper mapper,
            CertificadoRenderer renderer, CertificateAwsClient aws, @Value("${app.certificados.bucket:}") String bucket) {
        this.certificados = certificados; this.usuarios = usuarios; this.mapper = mapper;
        this.renderer = renderer; this.aws = aws; this.bucket = bucket;
    }
    @Transactional
    public Certificado procesar(byte[] data) {
        final Evento evento;
        try { evento = mapper.readValue(data, Evento.class); }
        catch (java.io.IOException ex) { throw new IllegalArgumentException("Evento de certificado invalido", ex); }
        validar(evento);
        var existing = certificados.findByIdUsuarioAndIdCursoAndFormato(evento.idUsuario(), evento.idCurso(), evento.formato());
        if (existing.isPresent()) return existing.get();
        var usuario = usuarios.findByIdExterna(evento.idUsuario())
                .orElseThrow(() -> new IllegalStateException("El titular aun no esta registrado"));
        if (usuario.getNombreUsuario() == null || usuario.getNombreUsuario().isBlank())
            throw new IllegalStateException("El titular no tiene nombre registrado");
        var c = new Certificado();
        c.setEventId(evento.eventId()); c.setCodigoVerificacion(UUID.randomUUID().toString());
        c.setIdUsuario(evento.idUsuario()); c.setNombreTitular(usuario.getNombreUsuario());
        c.setIdCurso(evento.idCurso()); c.setCodigoCurso(evento.codigoCurso());
        c.setNombreCurso(evento.nombreCurso()); c.setNivelCurso(evento.nivelCurso());
        c.setFormato(evento.formato()); c.setFechaCompletado(evento.fechaCompletado()); c.setFechaEmision(Instant.now());
        c.setBucket(bucket); c.setObjectKey("certificados/" + evento.eventId() + "." + evento.formato().toLowerCase(java.util.Locale.ROOT));
        byte[] file = renderer.render(c); c.setTamanoBytes(file.length);
        aws.putObject(bucket, c.getObjectKey(), file, "PDF".equals(c.getFormato()) ? "application/pdf" : "image/png");
        return certificados.saveAndFlush(c);
    }
    private void validar(Evento e) {
        if (e.version() != 1 || e.eventId() == null || e.idUsuario() == null || e.idUsuario().isBlank()
                || e.idUsuario().length() > 128 || e.idCurso() == null || e.idCurso() < 1
                || e.nombreCurso() == null || e.nombreCurso().isBlank() || e.nombreCurso().length() > 255
                || e.formato() == null || !Set.of("PDF", "PNG").contains(e.formato())
                || e.fechaCompletado() == null || e.fechaSolicitud() == null
                || e.fechaCompletado().isAfter(e.fechaSolicitud()) || e.fechaSolicitud().isAfter(Instant.now().plusSeconds(60)))
            throw new IllegalArgumentException("Contrato de certificado invalido");
        UUID.fromString(e.eventId());
    }
    public record Evento(int version, String eventId, String idUsuario, Long idCurso, String codigoCurso,
                         String nombreCurso, String nivelCurso, String formato, Instant fechaCompletado, Instant fechaSolicitud) {}
}
