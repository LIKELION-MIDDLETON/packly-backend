package com.jaungangton.api.common;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.jaungangton.api.auth.JwtKeyPair;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class BootstrapSecurityConfig {
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    JwtKeyPair jwtKeyPair(
            @Value("${centralton.jwt.public-key:}") String publicKey,
            @Value("${centralton.jwt.private-key:}") String privateKey,
            Environment environment) {
        if (publicKey.isBlank() || privateKey.isBlank()) {
            String datasourceUrl = environment.getProperty("spring.datasource.url", "");
            boolean localOrTest = environment.acceptsProfiles(Profiles.of("local", "dev", "test"))
                    || datasourceUrl.startsWith("jdbc:h2:");
            if (!localOrTest) {
                throw new IllegalStateException("JWT_PUBLIC_KEY and JWT_PRIVATE_KEY are required outside local/test");
            }
            return generateEphemeralKeyPair();
        }
        try {
            KeyFactory factory = KeyFactory.getInstance("RSA");
            RSAPublicKey rsaPublic = (RSAPublicKey) factory.generatePublic(
                    new X509EncodedKeySpec(decodePemOrBase64(publicKey, "PUBLIC KEY")));
            RSAPrivateKey rsaPrivate = (RSAPrivateKey) factory.generatePrivate(
                    new PKCS8EncodedKeySpec(decodePemOrBase64(privateKey, "PRIVATE KEY")));
            return new JwtKeyPair(rsaPublic, rsaPrivate);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException("JWT RSA keys are invalid", exception);
        }
    }

    @Bean
    JwtEncoder jwtEncoder(JwtKeyPair keys, @Value("${centralton.jwt.key-id}") String keyId) {
        RSAKey rsaKey = new RSAKey.Builder(keys.publicKey())
                .privateKey(keys.privateKey())
                .keyID(keyId)
                .build();
        JWKSource<SecurityContext> source = (selector, context) -> selector.select(new JWKSet(rsaKey));
        return new NimbusJwtEncoder(source);
    }

    @Bean
    JwtDecoder jwtDecoder(JwtKeyPair keys,
                          @Value("${centralton.jwt.issuer}") String issuer,
                          @Value("${centralton.jwt.audience}") String audience) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(keys.publicKey()).build();
        OAuth2TokenValidator<Jwt> issuerAndTime = JwtValidators.createDefaultWithIssuer(issuer);
        OAuth2TokenValidator<Jwt> audienceValidator = jwt -> jwt.getAudience().contains(audience)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(
                        new OAuth2Error("invalid_token", "Required audience is missing", null));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(issuerAndTime, audienceValidator));
        return decoder;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            @Qualifier("corsConfigurationSource") CorsConfigurationSource cors,
                                            ObjectMapper objectMapper) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .cors(corsConfigurer -> corsConfigurer.configurationSource(cors))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/health", "/actuator/health", "/actuator/info",
                                "/api/v1/auth/google", "/api/v1/auth/refresh",
                                "/api/v1/dev/auth/mock-google",
                                "/api/v1/internal/analyses/*/cnn-result").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        .authenticationEntryPoint((request, response, exception) ->
                                writeSecurityError(response, objectMapper, HttpServletResponse.SC_UNAUTHORIZED,
                                        "AUTHENTICATION_REQUIRED", "인증이 필요합니다.", request.getRequestURI()))
                        .accessDeniedHandler((request, response, exception) ->
                                writeSecurityError(response, objectMapper, HttpServletResponse.SC_FORBIDDEN,
                                        "ACCESS_DENIED", "이 요청에 대한 권한이 없습니다.", request.getRequestURI())))
                .build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${centralton.cors.allowed-origins:}") String allowedOrigins) {
        CorsConfiguration configuration = new CorsConfiguration();
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim).filter(value -> !value.isBlank()).toList();
        configuration.setAllowedOrigins(origins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key",
                "X-Centralton-Dev-Auth"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    private static byte[] decodePemOrBase64(String value, String label) {
        String normalized = value.replace("\\n", "\n")
                .replace("-----BEGIN " + label + "-----", "")
                .replace("-----END " + label + "-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(normalized);
    }

    private static JwtKeyPair generateEphemeralKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            java.security.KeyPair pair = generator.generateKeyPair();
            return new JwtKeyPair((RSAPublicKey) pair.getPublic(), (RSAPrivateKey) pair.getPrivate());
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Cannot create local JWT key", exception);
        }
    }

    private static void writeSecurityError(HttpServletResponse response, ObjectMapper objectMapper,
                                           int status, String code, String detail, String instance) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(),
                ApiError.of(org.springframework.http.HttpStatus.valueOf(status),
                        code, detail, instance, java.util.Map.of()));
    }
}
