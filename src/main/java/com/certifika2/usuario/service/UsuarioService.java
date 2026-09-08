package com.certifika2.usuario.service;

import com.certifika2.usuario.model.Usuario;
import com.certifika2.usuario.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;

    public Usuario registrarUsuario(String idExterna, String email, String nombre) {
        Optional<Usuario> usuarioExistente = usuarioRepository.findByIdExterna(idExterna);
        
        if (usuarioExistente.isPresent()) {
            return usuarioExistente.get();
        }

        Usuario nuevoUsuario = new Usuario();
        nuevoUsuario.setIdExterna(idExterna);
        nuevoUsuario.setEmail(email);
        nuevoUsuario.setNombreUsuario(nombre);
        
        Set<String> grupos = new HashSet<>();
        grupos.add("ROLE_USER"); // Rol por defecto
        nuevoUsuario.setGrupo(grupos);

        return usuarioRepository.save(nuevoUsuario);
    }

    public Usuario habilitarProfesor(Long idUsuario) {
        Usuario usuario = usuarioRepository.findById(idUsuario)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        
        usuario.getGrupo().add("ROLE_PROFESOR");
        return usuarioRepository.save(usuario);
    }

    public Optional<Usuario> obtenerUsuarioPorEmail(String email) {
        return usuarioRepository.findByEmail(email);
    }
}

