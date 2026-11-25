package com.certifika2.usuario.certificados;
import com.certifika2.usuario.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.util.Optional;

@WebMvcTest(CertificadoController.class) @Import(SecurityConfig.class)
class CertificadoSecurityTest {
    @Autowired MockMvc mvc;
    @MockBean CertificadoRepository repository;
    @MockBean CertificateAwsClient aws;
    @MockBean StreamCheckpointRepository checkpoints;
    @MockBean StreamFailureRepository failures;
    @MockBean JwtDecoder decoder;
    @Test void verificationIsPublicButDownloadsRequireSession() throws Exception {
        when(repository.findByCodigoVerificacion("unknown")).thenReturn(Optional.empty());
        mvc.perform(get("/api/certificados/verificar/unknown")).andExpect(status().isOk()).andExpect(jsonPath("valido").value(false));
        mvc.perform(get("/api/certificados/1/descarga")).andExpect(status().isUnauthorized());
        verifyNoInteractions(aws);
    }
    @Test void anotherUsersCertificateIsNotExposed() throws Exception {
        when(repository.findByIdCertificadoAndIdUsuario(1L, "outsider")).thenReturn(Optional.empty());
        mvc.perform(get("/api/certificados/1/descarga").with(jwt().jwt(j -> j.subject("outsider"))))
                .andExpect(status().isNotFound());
        verifyNoInteractions(aws);
    }
    @Test void studentCannotReadStreamDiagnostics() throws Exception {
        mvc.perform(get("/api/certificados/admin/entregas").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ESTUDIANTE"))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(checkpoints, failures);
    }
    @Test void adminCanCorrelateEmissionReceiptWithoutStorageDetails() throws Exception {
        var c = new Certificado(); c.setEventId("event-received"); c.setIdCurso(12L); c.setFormato("PDF");
        c.setBucket("private-bucket"); c.setObjectKey("private-key");
        when(repository.findAll(org.mockito.ArgumentMatchers.any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(java.util.List.of(c)));
        when(checkpoints.findAll(org.mockito.ArgumentMatchers.any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(org.springframework.data.domain.Page.empty());
        when(failures.findAll(org.mockito.ArgumentMatchers.any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(org.springframework.data.domain.Page.empty());
        mvc.perform(get("/api/certificados/admin/entregas").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.emitidos[0].eventId").value("event-received"))
                .andExpect(jsonPath("$.emitidos[0].bucket").doesNotExist());
    }
}
