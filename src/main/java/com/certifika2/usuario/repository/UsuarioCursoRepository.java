package com.certifika2.usuario.repository;

import com.certifika2.usuario.model.UsuarioCurso;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UsuarioCursoRepository extends JpaRepository<UsuarioCurso, Long> {
    List<UsuarioCurso> findByIdUsuario(Long idUsuario);
    List<UsuarioCurso> findByIdCurso(Long idCurso);
}

