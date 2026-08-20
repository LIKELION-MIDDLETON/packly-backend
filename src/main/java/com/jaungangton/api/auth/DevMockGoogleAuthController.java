package com.jaungangton.api.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import com.jaungangton.api.common.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Profile({"local", "dev"})
@Conditional(DevMockGoogleAuthEnabledCondition.class)
@RestController
@RequestMapping("/api/v1/dev/auth")
public class DevMockGoogleAuthController {
    private static final String SUBJECT = "centralton-dev-google-subject";
    private static final String EMAIL = "centralton.dev@example.com";
    private final AuthService authService;
    private final String headerValue;

    public DevMockGoogleAuthController(
            AuthService authService,
            @Value("${centralton.dev.auth.header-value:}") String headerValue) {
        this.authService = authService;
        this.headerValue = headerValue;
    }

    @PostMapping("/mock-google")
    AuthController.AuthResponse mockGoogle(
            @RequestHeader(value = "X-Centralton-Dev-Auth", required = false) String suppliedHeader) {
        if (headerValue.isBlank()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "DEV_AUTH_NOT_CONFIGURED",
                    "개발용 인증이 설정되지 않았습니다.");
        }
        boolean matches = suppliedHeader != null && MessageDigest.isEqual(
                headerValue.getBytes(StandardCharsets.UTF_8), suppliedHeader.getBytes(StandardCharsets.UTF_8));
        if (!matches) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_DEV_AUTH", "개발용 인증 정보가 올바르지 않습니다.");
        }
        AuthPersistenceService.AuthResult result = authService.exchangeVerifiedGoogle(
                new GoogleTokenVerifierPort.VerifiedGoogleIdentity(
                        SUBJECT, EMAIL, true, "Centralton Dev User", null), true);
        return AuthController.AuthResponse.from(result);
    }
}
