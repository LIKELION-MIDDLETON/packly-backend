package com.jaungangton.api.auth;

import java.util.UUID;

import com.jaungangton.api.auth.GoogleTokenVerifierPort.VerifiedGoogleIdentity;
import com.jaungangton.api.common.ApiException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final GoogleTokenVerifierPort googleVerifier;
    private final AuthPersistenceService persistence;

    public AuthService(GoogleTokenVerifierPort googleVerifier, AuthPersistenceService persistence) {
        this.googleVerifier = googleVerifier;
        this.persistence = persistence;
    }

    AuthPersistenceService.AuthResult exchangeGoogle(String idToken, Boolean termsAccepted) {
        VerifiedGoogleIdentity google = googleVerifier.verify(idToken);
        return exchangeVerifiedGoogle(google, termsAccepted);
    }

    AuthPersistenceService.AuthResult exchangeVerifiedGoogle(VerifiedGoogleIdentity google, Boolean termsAccepted) {
        if (google.subject() == null || google.subject().isBlank() || google.email() == null
                || google.email().isBlank() || !google.emailVerified()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNVERIFIED_GOOGLE_IDENTITY",
                    "검증된 Google 계정 정보가 필요합니다.");
        }
        try {
            return persistence.exchangeGoogle(google, termsAccepted);
        } catch (DataIntegrityViolationException conflict) {
            AuthPersistenceService.AuthResult existing = persistence.exchangeExistingGoogle(google);
            if (existing != null) return existing;
            throw conflict;
        }
    }

    AuthPersistenceService.AuthResult refresh(String refreshToken) {
        return persistence.rotate(refreshToken);
    }

    void logout(UUID userId, String refreshToken) {
        persistence.logout(userId, refreshToken);
    }

    User me(UUID userId) {
        return persistence.requireUser(userId);
    }
}
