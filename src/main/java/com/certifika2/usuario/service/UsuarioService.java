package com.certifika2.usuario.service;

import com.certifika2.usuario.model.Usuario;
import com.certifika2.usuario.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

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
            Usuario actual = usuarioExistente.get();
            boolean changed = false;
            if (nombre != null && !nombre.isBlank() && !nombre.equals(actual.getNombreUsuario())) {
                actual.setNombreUsuario(nombre); changed = true;
            }
            if (email != null && !email.isBlank() && !email.equals(actual.getEmail())) {
                if (usuarioRepository.findByEmail(email).filter(other -> !other.getIdExterna().equals(idExterna)).isPresent()) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "El correo ya esta asociado con otra identidad");
                }
                actual.setEmail(email); changed = true;
            }
            return changed ? usuarioRepository.saveAndFlush(actual) : actual;
        }

        Usuario nuevoUsuario = new Usuario();
        nuevoUsuario.setIdExterna(idExterna);
        nuevoUsuario.setEmail(email);
        nuevoUsuario.setNombreUsuario(nombre);
        
        Set<String> grupos = new HashSet<>();
        grupos.add("ROLE_ESTUDIANTE"); // Every new account starts as a student.
        nuevoUsuario.setGrupo(grupos);

        try {
            return usuarioRepository.saveAndFlush(nuevoUsuario);
        } catch (DataIntegrityViolationException duplicate) {
            // Repository transactions have already rolled back here; reread the concurrent winner.
            var winner = usuarioRepository.findByIdExterna(idExterna);
            if (winner.isPresent()) return winner.get();
            if (usuarioRepository.findByEmail(email).isPresent()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "El correo ya esta asociado con otra identidad", duplicate);
            }
            throw duplicate;
        }
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

