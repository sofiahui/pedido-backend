package cl.duocuc.pedidos360.bff.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AudienceValidatorTest {

    private Jwt jwtWithAudience(String audience) {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim("aud", List.of(audience))
                .audience(List.of(audience))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .claims(claims -> claims.putAll(Map.of("sub", "user-1")))
                .build();
    }

    @Test
    void aceptaTokenConAudienceEsperado() {
        AudienceValidator validator = new AudienceValidator("api://expected-client-id");

        OAuth2TokenValidatorResult result = validator.validate(jwtWithAudience("api://expected-client-id"));

        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    void rechazaTokenConAudienceDistinto() {
        AudienceValidator validator = new AudienceValidator("api://expected-client-id");

        OAuth2TokenValidatorResult result = validator.validate(jwtWithAudience("api://otro-client-id"));

        assertThat(result.hasErrors()).isTrue();
    }
}
