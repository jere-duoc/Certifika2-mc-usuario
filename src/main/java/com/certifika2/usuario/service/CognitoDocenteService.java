package com.certifika2.usuario.service;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import java.util.stream.Collectors;

@Service
public class CognitoDocenteService {
    private final CognitoIdentityProviderClient client;
    private final String pool;
    public CognitoDocenteService(CognitoIdentityProviderClient client,
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuer) {
        this.client=client; this.pool=issuer.substring(issuer.lastIndexOf('/')+1);
    }
    public Identidad identidad(Jwt jwt) {
        String username=jwt.getClaimAsString("username");
        if(username==null) username=jwt.getClaimAsString("cognito:username");
        if(username==null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Identidad Cognito incompleta");
        final String nombre=username;
        var user=client.adminGetUser(r -> r.userPoolId(pool).username(nombre));
        var attrs=user.userAttributes().stream().collect(Collectors.toMap(a->a.name(),a->a.value()));
        if(!jwt.getSubject().equals(attrs.get("sub")) || attrs.getOrDefault("email","").isBlank())
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Identidad no coincide con Cognito");
        return new Identidad(user.username(),attrs.getOrDefault("name",attrs.get("email")),attrs.get("email"));
    }
    public void habilitar(String username) {
        client.adminAddUserToGroup(r -> r.userPoolId(pool).username(username).groupName("PROFESOR"));
    }
    public record Identidad(String username,String name,String email) {}
    @Configuration
    static class Cliente {
        @Bean CognitoIdentityProviderClient cognitoDocenteClient(@Value("${AWS_REGION:us-east-1}") String region) {
            return CognitoIdentityProviderClient.builder().region(Region.of(region)).build();
        }
    }
}
