package com.certifika2.usuario.controller;
import com.certifika2.usuario.config.SecurityConfig;
import com.certifika2.usuario.service.PostulacionService;
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
import org.springframework.security.core.authority.SimpleGrantedAuthority;

@WebMvcTest(PostulacionController.class) @Import(SecurityConfig.class)
class PostulacionSecurityTest {
    @Autowired MockMvc mvc;
    @MockBean PostulacionService service;
    @MockBean JwtDecoder decoder;
    @Test void studentCannotSeeAllApplications() throws Exception {
        mvc.perform(get("/api/usuarios/postulaciones").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ESTUDIANTE"))))
                .andExpect(status().isForbidden());
    }
    @Test void studentCannotReview() throws Exception {
        mvc.perform(put("/api/usuarios/postulaciones/id/revision").contentType("application/json").content("{\"status\":\"APPROVED\"}")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ESTUDIANTE")))).andExpect(status().isForbidden());
    }
    @Test void adminCanList() throws Exception {
        mvc.perform(get("/api/usuarios/postulaciones").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk());
    }
    @Test void anonymousCannotList() throws Exception {
        mvc.perform(get("/api/usuarios/postulaciones")).andExpect(status().isUnauthorized());
    }
}
