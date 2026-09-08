package com.certifika2.usuario.controller;

import com.certifika2.usuario.model.Usuario;
import com.certifika2.usuario.service.UsuarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/usuarios")
@RequiredArgsConstructor
public class UsuarioController {

    private final UsuarioService usuarioService;

    @PostMapping("/registro")
    public ResponseEntity<Usuario> registrarOObtenerUsuario(@AuthenticationPrincipal Jwt jwt) {
        String idExterna = jwt.getSubject();
        String email = jwt.getClaimAsString("email");
        String nombre = jwt.getClaimAsString("name");

        Usuario usuario = usuarioService.registrarUsuario(idExterna, email, nombre);
        return ResponseEntity.ok(usuario);
    }

    @PutMapping("/{id}/habilitar-profesor")
    public ResponseEntity<Usuario> habilitarProfesor(@PathVariable Long id) {
        // Todo: Añadir validación para que solo ROLE_ADMIN pueda hacer esto
        Usuario usuarioActualizado = usuarioService.habilitarProfesor(id);
        return ResponseEntity.ok(usuarioActualizado);
    }
}

