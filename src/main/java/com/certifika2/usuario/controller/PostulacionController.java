package com.certifika2.usuario.controller;
import com.certifika2.usuario.service.PostulacionService;
import com.certifika2.usuario.model.Postulacion.Estado;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.http.*;
import java.util.List;

@RestController @RequestMapping("/api/usuarios/postulaciones") @RequiredArgsConstructor
public class PostulacionController {
    private final PostulacionService service;
    @GetMapping("/mia") public ResponseEntity<PostulacionService.Vista> mia(@AuthenticationPrincipal Jwt jwt) {
        var result=service.mia(jwt.getSubject()); return result==null?ResponseEntity.noContent().build():ResponseEntity.ok(result);
    }
    @PostMapping @PreAuthorize("hasRole('ESTUDIANTE') and !hasAnyRole('PROFESOR','ADMIN')")
    public PostulacionService.Vista enviar(@AuthenticationPrincipal Jwt jwt,@RequestBody PostulacionService.Entrada entrada) {
        return service.enviar(jwt,entrada);
    }
    @GetMapping @PreAuthorize("hasRole('ADMIN')") public List<PostulacionService.Vista> listar() { return service.listar(); }
    @PutMapping("/{id}/revision") @PreAuthorize("hasRole('ADMIN')")
    public PostulacionService.Vista revisar(@PathVariable String id,@RequestBody Revision revision,@AuthenticationPrincipal Jwt jwt) {
        return service.revisar(id,revision.status(),jwt.getSubject());
    }
    public record Revision(Estado status) {}
    @ExceptionHandler(software.amazon.awssdk.core.exception.SdkException.class)
    ResponseEntity<java.util.Map<String,String>> awsFailure() {
        return ResponseEntity.status(503).body(java.util.Map.of("error","Cognito no está disponible o no autoriza la operación; la solicitud no se aprobó. Intenta nuevamente."));
    }
}
