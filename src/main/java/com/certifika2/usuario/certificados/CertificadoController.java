package com.certifika2.usuario.certificados;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.http.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import java.time.Instant;
import java.util.List;

@RestController @RequestMapping("/api/certificados") @RequiredArgsConstructor
public class CertificadoController {
    private final CertificadoRepository repository;
    private final CertificateAwsClient aws;
    private final StreamCheckpointRepository checkpoints;
    private final StreamFailureRepository failures;
    @Value("${app.certificados.enabled:false}") private boolean enabled;
    @GetMapping
    public List<Vista> listar(@AuthenticationPrincipal Jwt jwt) {
        return repository.findByIdUsuarioOrderByFechaEmisionDesc(jwt.getSubject()).stream().map(Vista::of).toList();
    }
    @GetMapping("/{id}")
    public Vista obtener(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) { return Vista.of(titular(id, jwt)); }
    @GetMapping("/{id}/descarga")
    public ResponseEntity<byte[]> descargar(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        var c = titular(id, jwt);
        final byte[] file;
        try { file = aws.getObject(c.getBucket(), c.getObjectKey()); }
        catch (RuntimeException ex) { throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Archivo temporalmente no disponible"); }
        String ext = c.getFormato().toLowerCase(java.util.Locale.ROOT);
        return ResponseEntity.ok().contentType("PDF".equals(c.getFormato()) ? MediaType.APPLICATION_PDF : MediaType.IMAGE_PNG)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("certificado-" + c.getIdCertificado() + "." + ext).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store").body(file);
    }
    @GetMapping("/verificar/{codigo}")
    public Verificacion verificar(@PathVariable String codigo) {
        return repository.findByCodigoVerificacion(codigo).map(c -> new Verificacion(true, c.getCodigoVerificacion(),
                c.getNombreTitular(), c.getNombreCurso(), c.getNivelCurso(), c.getFechaEmision()))
                .orElseGet(() -> new Verificacion(false, codigo, null, null, null, null));
    }
    @GetMapping("/admin/entregas") @PreAuthorize("hasRole('ADMIN')")
    public Monitor entregas() {
        return new Monitor(enabled, checkpoints.findAll(PageRequest.of(0, 200, Sort.by(Sort.Direction.DESC, "actualizadoEn"))).getContent(),
                failures.findAll(PageRequest.of(0, 200, Sort.by(Sort.Direction.DESC, "ultimoIntento"))).getContent(),
                repository.findAll(PageRequest.of(0, 200, Sort.by(Sort.Direction.DESC, "fechaEmision")))
                        .stream().map(c -> new Recibo(c.getEventId(), c.getIdCurso(), c.getFormato(), c.getFechaEmision())).toList());
    }
    private Certificado titular(Long id, Jwt jwt) {
        return repository.findByIdCertificadoAndIdUsuario(id, jwt.getSubject())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Certificado no encontrado"));
    }
    public record Vista(Long idCertificado, String codigoVerificacion, String idUsuario, String nombreTitular,
                        Long idCurso, String codigoCurso, String nombreCurso, String nivelCurso, String formato,
                        long tamanoBytes, Instant fechaCompletado, Instant fechaEmision) {
        static Vista of(Certificado c) { return new Vista(c.getIdCertificado(), c.getCodigoVerificacion(), c.getIdUsuario(),
                c.getNombreTitular(), c.getIdCurso(), c.getCodigoCurso(), c.getNombreCurso(), c.getNivelCurso(),
                c.getFormato(), c.getTamanoBytes(), c.getFechaCompletado(), c.getFechaEmision()); }
    }
    public record Verificacion(boolean valido, String codigoVerificacion, String nombreTitular, String nombreCurso,
                               String nivelCurso, Instant fechaEmision) {}
    public record Recibo(String eventId, Long idCurso, String formato, Instant emitidoEn) {}
    public record Monitor(boolean consumidorHabilitado, List<StreamCheckpoint> checkpoints, List<StreamFailure> errores,
                          List<Recibo> emitidos) {}
}
