package com.certifika2.usuario.controller;

import com.certifika2.usuario.model.Usuario;
import com.certifika2.usuario.service.UsuarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/usuarios")
@RequiredArgsConstructor
public class UsuarioController {

    private final UsuarioService usuarioService;

    @PostMapping("/registro")
    public ResponseEntity<Usuario> registrarOObtenerUsuario(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody RegistroUsuarioRequest profile
    ) {
        String idExterna = jwt.getSubject();
        String email = firstNonBlank(jwt.getClaimAsString("email"), profile.email());
        String nombre = firstNonBlank(jwt.getClaimAsString("name"), profile.nombre(), email);

        if (email == null) {
            return ResponseEntity.badRequest().build();
        }

        Usuario usuario = usuarioService.registrarUsuario(idExterna, email, nombre);
        return ResponseEntity.ok(usuario);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    public record RegistroUsuarioRequest(String email, String nombre) {}

    @PutMapping("/{id}/habilitar-profesor")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Usuario> habilitarProfesor(@PathVariable Long id) {
        Usuario usuarioActualizado = usuarioService.habilitarProfesor(id);
        return ResponseEntity.ok(usuarioActualizado);
    }
}

