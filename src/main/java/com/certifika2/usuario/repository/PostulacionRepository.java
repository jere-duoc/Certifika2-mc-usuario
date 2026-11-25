package com.certifika2.usuario.repository;
import com.certifika2.usuario.model.Postulacion;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface PostulacionRepository extends JpaRepository<Postulacion,String> {
    Optional<Postulacion> findByApplicantId(String applicantId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Postulacion p where p.applicantId = :sub")
    Optional<Postulacion> bloquearPorApplicantId(String sub);
    List<Postulacion> findAllByOrderByCreatedAtDesc();
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Postulacion p where p.id = :id")
    Optional<Postulacion> bloquear(String id);
}
