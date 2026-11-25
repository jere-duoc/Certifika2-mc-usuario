package com.certifika2.usuario.repository;

import com.certifika2.usuario.model.Certificado;
import com.certifika2.usuario.model.FormatoCertificado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CertificadoRepository extends JpaRepository<Certificado, Long> {

    List<Certificado> findByIdExternaUsuarioOrderByFechaEmisionDesc(String idExternaUsuario);

    Optional<Certificado> findByCodigoVerificacion(String codigoVerificacion);

    boolean existsByEventId(String eventId);

    Optional<Certificado> findByIdExternaUsuarioAndIdCursoAndFormato(String idExternaUsuario, Long idCurso,
                                                                     FormatoCertificado formato);
}
