package com.certifika2.usuario.service;

import com.certifika2.usuario.controller.dto.CertificadoResponse;
import com.certifika2.usuario.controller.dto.VerificacionResponse;
import com.certifika2.usuario.exception.EventoNoRecuperableException;
import com.certifika2.usuario.messaging.FirehoseCertificadoPublisher;
import com.certifika2.usuario.messaging.dto.CertificadoEmitidoEvent;
import com.certifika2.usuario.messaging.dto.CertificadoSolicitadoEvent;
import com.certifika2.usuario.model.Certificado;
import com.certifika2.usuario.model.FormatoCertificado;
import com.certifika2.usuario.model.Usuario;
import com.certifika2.usuario.repository.CertificadoRepository;
import com.certifika2.usuario.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Lógica de dominio de certificados, independiente de Kinesis: recibe un evento ya
 * deserializado, genera el archivo, lo guarda en S3 y lo vincula al usuario.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CertificadoService {

    private final CertificadoRepository certificadoRepository;
    private final UsuarioRepository usuarioRepository;
    private final CertificadoGenerator generator;
    private final CertificadoStorage storage;
    private final FirehoseCertificadoPublisher firehosePublisher;

    /**
     * Procesa una solicitud. Es idempotente: si el evento ya se procesó, o el usuario
     * ya tiene el certificado de ese curso en ese formato, no genera otro.
     */
    @Transactional
    public Optional<Certificado> procesarSolicitud(CertificadoSolicitadoEvent evento) {
        validar(evento);

        if (certificadoRepository.existsByEventId(evento.eventId())) {
            log.info("Evento {} ya procesado (duplicado de Kinesis), se omite", evento.eventId());
            return Optional.empty();
        }

        FormatoCertificado formato = evento.formato() == null ? FormatoCertificado.PDF : evento.formato();
        String sub = evento.idUsuario();

        Optional<Certificado> existente = certificadoRepository
                .findByIdExternaUsuarioAndIdCursoAndFormato(sub, evento.idCurso(), formato);
        if (existente.isPresent()) {
            log.info("Usuario {} ya tiene certificado {} del curso {}, se omite evento {}",
                    sub, formato, evento.idCurso(), evento.eventId());
            return existente;
        }

        // Vínculo con el usuario registrado (si ya se registró en ms-usuarios)
        Usuario usuario = usuarioRepository.findByIdExterna(sub).orElse(null);
        String nombre = primeroNoVacio(
                usuario != null ? usuario.getNombreUsuario() : null,
                evento.nombreUsuario(),
                usuario != null ? usuario.getEmail() : null,
                evento.emailUsuario(),
                "Estudiante Certifika2");
        Instant fechaCompletado = evento.fechaCompletado() != null ? evento.fechaCompletado() : evento.occurredAt();
        String codigo = UUID.randomUUID().toString();

        byte[] archivo = generator.generar(formato, new CertificadoGenerator.DatosCertificado(
                nombre, evento.nombreCurso(), evento.nivelCurso(), fechaCompletado, codigo));

        // Clave determinística: si el evento se reintenta, se sobrescribe el mismo objeto
        // en vez de dejar archivos huérfanos en S3.
        String key = "certificados/%s/%d/certificado-curso-%d.%s".formatted(
                sub, evento.idCurso(), evento.idCurso(), formato.getExtension());
        storage.subir(key, archivo, formato.getContentType());

        Certificado certificado = new Certificado();
        certificado.setCodigoVerificacion(codigo);
        certificado.setEventId(evento.eventId());
        certificado.setIdExternaUsuario(sub);
        certificado.setUsuario(usuario);
        certificado.setNombreTitular(nombre);
        certificado.setIdCurso(evento.idCurso());
        certificado.setCodigoCurso(evento.codigoCurso());
        certificado.setNombreCurso(evento.nombreCurso());
        certificado.setNivelCurso(evento.nivelCurso());
        certificado.setFormato(formato);
        certificado.setS3Bucket(storage.bucket());
        certificado.setS3Key(key);
        certificado.setTamanoBytes((long) archivo.length);
        certificado.setFechaCompletado(fechaCompletado);
        certificado.setFechaEmision(Instant.now());
        Certificado guardado = certificadoRepository.saveAndFlush(certificado);

        long latenciaMs = evento.occurredAt() != null
                ? Duration.between(evento.occurredAt(), guardado.getFechaEmision()).toMillis() : -1;
        log.info("Certificado {} emitido para usuario {} curso {} ({} bytes, {} ms desde la solicitud)",
                codigo, sub, evento.idCurso(), archivo.length, latenciaMs);

        firehosePublisher.publicar(new CertificadoEmitidoEvent(
                CertificadoEmitidoEvent.TYPE, evento.eventId(), codigo, guardado.getIdCertificado(),
                sub, evento.idCurso(), evento.codigoCurso(), evento.nombreCurso(), evento.nivelCurso(),
                formato.name(), guardado.getTamanoBytes(), fechaCompletado, guardado.getFechaEmision(), latenciaMs));

        return Optional.of(guardado);
    }

    private void validar(CertificadoSolicitadoEvent e) {
        if (e.eventId() == null || e.eventId().isBlank()) {
            throw new EventoNoRecuperableException("Evento sin eventId");
        }
        if (!CertificadoSolicitadoEvent.TYPE.equals(e.eventType())) {
            throw new EventoNoRecuperableException("Tipo de evento no soportado: " + e.eventType());
        }
        if (e.version() == null || !e.version().startsWith("1.")) {
            throw new EventoNoRecuperableException("Versión de contrato no soportada: " + e.version());
        }
        if (e.idUsuario() == null || e.idUsuario().isBlank() || e.idCurso() == null
                || e.nombreCurso() == null || e.nombreCurso().isBlank()) {
            throw new EventoNoRecuperableException("Evento " + e.eventId() + " con datos obligatorios faltantes");
        }
    }

    private static String primeroNoVacio(String... valores) {
        for (String v : valores) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }

    // ---------- Consultas para la API REST ----------

    @Transactional(readOnly = true)
    public List<CertificadoResponse> listarPorUsuario(String sub) {
        return certificadoRepository.findByIdExternaUsuarioOrderByFechaEmisionDesc(sub).stream()
                .map(CertificadoResponse::de)
                .toList();
    }

    @Transactional(readOnly = true)
    public CertificadoResponse obtener(Long idCertificado, String sub) {
        return CertificadoResponse.de(buscarPropio(idCertificado, sub));
    }

    @Transactional(readOnly = true)
    public ArchivoCertificado descargar(Long idCertificado, String sub) {
        Certificado c = buscarPropio(idCertificado, sub);
        byte[] bytes = storage.descargar(c.getS3Bucket(), c.getS3Key());
        String base = c.getCodigoCurso() != null ? c.getCodigoCurso() : c.getNombreCurso();
        String nombreArchivo = "certificado-%s.%s".formatted(
                base.replaceAll("[^A-Za-z0-9-]+", "_"), c.getFormato().getExtension());
        return new ArchivoCertificado(bytes, c.getFormato().getContentType(), nombreArchivo);
    }

    @Transactional(readOnly = true)
    public VerificacionResponse verificar(String codigo) {
        return certificadoRepository.findByCodigoVerificacion(codigo)
                .map(VerificacionResponse::valido)
                .orElseGet(() -> VerificacionResponse.invalido(codigo));
    }

    private Certificado buscarPropio(Long idCertificado, String sub) {
        Certificado c = certificadoRepository.findById(idCertificado)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Certificado no encontrado"));
        if (!c.getIdExternaUsuario().equals(sub)) {
            // 404 y no 403: no revelar que el certificado existe
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Certificado no encontrado");
        }
        return c;
    }

    public record ArchivoCertificado(byte[] contenido, String contentType, String nombreArchivo) {
    }
}
