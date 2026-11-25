package com.certifika2.usuario.certificados;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
public interface CertificadoRepository extends JpaRepository<Certificado, Long> {
    Optional<Certificado> findByIdUsuarioAndIdCursoAndFormato(String usuario, Long curso, String formato);
    Optional<Certificado> findByIdCertificadoAndIdUsuario(Long id, String usuario);
    Optional<Certificado> findByCodigoVerificacion(String codigo);
    List<Certificado> findByIdUsuarioOrderByFechaEmisionDesc(String usuario);
}
