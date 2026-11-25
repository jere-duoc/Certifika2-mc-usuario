package com.certifika2.usuario.service;
import com.certifika2.usuario.model.Postulacion.Estado;
import com.certifika2.usuario.repository.PostulacionRepository;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jwt.Jwt;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest @Import(PostulacionService.class)
class PostulacionServiceTest {
    @Autowired PostulacionService service;
    @Autowired PostulacionRepository repo;
    @MockBean CognitoDocenteService cognito;
    Jwt jwt=Jwt.withTokenValue("test").header("alg","none").subject("student").claim("username","google_student").build();
    PostulacionService.Entrada input=new PostulacionService.Entrada("Cloud",2,"https://example.com/perfil",
            "Tengo experiencia profesional y quiero compartir mis conocimientos con estudiantes.","2 a 4 horas");
    @BeforeEach void identity() { when(cognito.identidad(any())).thenReturn(new CognitoDocenteService.Identidad("google_student","Alumno","student@example.com")); }
    @Test void savedApplicationIsVisibleToAdminAndOnlyOwner() {
        var p=service.enviar(jwt,input);
        assertThat(service.listar()).hasSize(1);
        assertThat(service.mia("student").id()).isEqualTo(p.id());
        assertThat(service.mia("outsider")).isNull();
        assertThat(p.applicantEmail()).isEqualTo("student@example.com");
    }
    @Test void resubmissionUpdatesSameRecord() {
        var p=service.enviar(jwt,input); var update=service.enviar(jwt,input);
        assertThat(update.id()).isEqualTo(p.id()); assertThat(repo.count()).isEqualTo(1);
    }
    @Test void approvalGrantsCognitoRoleOnceAndCannotBeReversedThroughRejection() {
        var p=service.enviar(jwt,input);
        assertThat(service.revisar(p.id(),Estado.APPROVED,"admin").status()).isEqualTo(Estado.APPROVED);
        service.revisar(p.id(),Estado.APPROVED,"admin");
        verify(cognito,times(1)).habilitar("google_student");
        assertThatThrownBy(()->service.revisar(p.id(),Estado.REJECTED,"admin")).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }
    @Test void failedCognitoCallDoesNotMarkApplicationApproved() {
        var p=service.enviar(jwt,input);
        doThrow(new IllegalStateException("Cognito unavailable")).when(cognito).habilitar(any());
        assertThatThrownBy(()->service.revisar(p.id(),Estado.APPROVED,"admin")).isInstanceOf(IllegalStateException.class);
        assertThat(repo.findById(p.id()).orElseThrow().getStatus()).isEqualTo(Estado.PENDING);
    }
    @Test void rejectedApplicantCanCorrectAndSubmitAgain() {
        var p=service.enviar(jwt,input); service.revisar(p.id(),Estado.REJECTED,"admin");
        assertThat(service.enviar(jwt,input).status()).isEqualTo(Estado.PENDING);
        verify(cognito,never()).habilitar(any());
    }
    @Test void malformedApplicationsAreNotSaved() {
        assertThatThrownBy(()->service.enviar(jwt,new PostulacionService.Entrada("Cloud",2,"javascript:alert(1)",input.motivation(),"2 horas")))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(repo.count()).isZero(); verifyNoInteractions(cognito);
    }
}
