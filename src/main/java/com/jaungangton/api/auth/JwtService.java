package com.jaungangton.api.auth;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private final JwtEncoder encoder;
    private final Clock clock;
    private final String issuer;
    private final String audience;
    private final String keyId;
    private final long accessTtlSeconds;

    public JwtService(JwtEncoder encoder, Clock clock,
                      @Value("${centralton.jwt.issuer}") String issuer,
                      @Value("${centralton.jwt.audience}") String audience,
                      @Value("${centralton.jwt.key-id}") String keyId,
                      @Value("${centralton.jwt.access-ttl-seconds:900}") long accessTtlSeconds) {
        this.encoder = encoder;
        this.clock = clock;
        this.issuer = issuer;
        this.audience = audience;
        this.keyId = keyId;
        this.accessTtlSeconds = accessTtlSeconds;
    }

    IssuedAccessToken issue(UUID userId, UUID sessionId) {
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plusSeconds(accessTtlSeconds);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer).audience(List.of(audience)).subject(userId.toString())
                .id(UUID.randomUUID().toString()).issuedAt(issuedAt).expiresAt(expiresAt)
                .claim("role", "USER").claim("roles", List.of("USER"))
                .claim("sid", sessionId.toString()).build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(keyId).build();
        return new IssuedAccessToken(
                encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue(), expiresAt);
    }

    record IssuedAccessToken(String value, Instant expiresAt) {
    }
}
