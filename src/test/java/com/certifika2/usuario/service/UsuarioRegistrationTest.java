package com.certifika2.usuario.service;

import com.certifika2.usuario.model.Usuario;
import com.certifika2.usuario.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.server.ResponseStatusException;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UsuarioRegistrationTest {
    @Test void concurrentRegistrationReturnsTheExistingIdentity() {
        var repository = mock(UsuarioRepository.class);
        var existing = new Usuario(); existing.setIdExterna("same-sub");
        when(repository.findByIdExterna("same-sub")).thenReturn(Optional.empty(), Optional.of(existing));
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate"));
        assertThat(new UsuarioService(repository).registrarUsuario("same-sub", "student@example.invalid", "Student"))
            .isSameAs(existing);
        verify(repository, never()).findByEmail(any());
    }

    @Test void duplicateEmailCannotReassignAnotherIdentity() {
        var repository = mock(UsuarioRepository.class);
        when(repository.findByIdExterna("new-sub")).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate"));
        when(repository.findByEmail("student@example.invalid")).thenReturn(Optional.of(new Usuario()));
        assertThatThrownBy(() -> new UsuarioService(repository).registrarUsuario("new-sub", "student@example.invalid", "Student"))
            .isInstanceOfSatisfying(ResponseStatusException.class, error -> assertThat(error.getStatusCode().value()).isEqualTo(409));
    }
}
