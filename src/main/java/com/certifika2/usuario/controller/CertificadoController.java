package com.certifika2.usuario.controller;

import com.certifika2.usuario.controller.dto.CertificadoResponse;
import com.certifika2.usuario.controller.dto.VerificacionResponse;
import com.certifika2.usuario.service.CertificadoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Certificados del usuario autenticado (el titular se identifica por el `sub` del JWT). */
@RestController
@RequestMapping("/api/certificados")
@RequiredArgsConstructor
public class CertificadoController {

    private final CertificadoService certificadoService;

    @GetMapping
    public ResponseEntity<List<CertificadoResponse>> misCertificados(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(certificadoService.listarPorUsuario(jwt.getSubject()));
    }

    @GetMapping("/{idCertificado}")
    public ResponseEntity<CertificadoResponse> obtener(@PathVariable Long idCertificado, @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(certificadoService.obtener(idCertificado, jwt.getSubject()));
    }

    /** Descarga el archivo (PDF o PNG) desde S3. */
    @GetMapping("/{idCertificado}/descarga")
    public ResponseEntity<byte[]> descargar(@PathVariable Long idCertificado, @AuthenticationPrincipal Jwt jwt) {
        CertificadoService.ArchivoCertificado archivo = certificadoService.descargar(idCertificado, jwt.getSubject());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(archivo.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(archivo.nombreArchivo()).build().toString())
                .body(archivo.contenido());
    }

    /** Público: permite a un tercero comprobar que un certificado es auténtico. */
    @GetMapping("/verificar/{codigo}")
    public ResponseEntity<VerificacionResponse> verificar(@PathVariable String codigo) {
        return ResponseEntity.ok(certificadoService.verificar(codigo));
    }
}
