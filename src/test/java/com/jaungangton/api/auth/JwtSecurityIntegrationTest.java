package com.jaungangton.api.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;

@SpringBootTest
@AutoConfigureMockMvc
class JwtSecurityIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired JwtEncoder encoder;

    @Test
    void rejectsWrongIssuerAudienceExpiredAndTamperedSignature() throws Exception {
        Instant now = Instant.now();
        String wrongIssuer = token("other-api", "centralton-mobile", now.minusSeconds(10), now.plusSeconds(60));
        String wrongAudience = token("centralton-api", "other-client", now.minusSeconds(10), now.plusSeconds(60));
        String expired = token("centralton-api", "centralton-mobile", now.minusSeconds(120), now.minusSeconds(60));
        String valid = token("centralton-api", "centralton-mobile", now.minusSeconds(10), now.plusSeconds(60));
        int signatureStart = valid.lastIndexOf('.') + 1;
        int mutationIndex = signatureStart + (valid.length() - signatureStart) / 2;
        char original = valid.charAt(mutationIndex);
        String tampered = valid.substring(0, mutationIndex) + (original == 'A' ? 'B' : 'A')
                + valid.substring(mutationIndex + 1);

        assertUnauthorized(wrongIssuer);
        assertUnauthorized(wrongAudience);
        assertUnauthorized(expired);
        assertUnauthorized(tampered);
    }

    @Test
    void devMockRouteIsAbsentWithoutDevelopmentProfile() throws Exception {
        mockMvc.perform(post("/api/v1/dev/auth/mock-google")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    private String token(String issuer, String audience, Instant issuedAt, Instant expiresAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer).audience(List.of(audience)).subject(UUID.randomUUID().toString())
                .id(UUID.randomUUID().toString()).issuedAt(issuedAt).expiresAt(expiresAt)
                .claim("role", "USER").claim("roles", List.of("USER"))
                .claim("sid", UUID.randomUUID().toString()).build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId("test-key").build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private void assertUnauthorized(String token) throws Exception {
        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }
}
