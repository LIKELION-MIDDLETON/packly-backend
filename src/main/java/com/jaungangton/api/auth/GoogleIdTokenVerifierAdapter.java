package com.jaungangton.api.auth;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.jaungangton.api.common.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class GoogleIdTokenVerifierAdapter implements GoogleTokenVerifierPort {
    private final String serverClientId;
    private final GoogleIdTokenVerifier verifier;

    public GoogleIdTokenVerifierAdapter(@Value("${centralton.google.server-client-id:}") String serverClientId) {
        this.serverClientId = serverClientId;
        this.verifier = serverClientId.isBlank() ? null : new GoogleIdTokenVerifier.Builder(
                new NetHttpTransport(), GsonFactory.getDefaultInstance())
                .setAudience(List.of(serverClientId))
                .build();
    }

    @Override
    public VerifiedGoogleIdentity verify(String idToken) {
        if (serverClientId.isBlank()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "GOOGLE_AUTH_NOT_CONFIGURED",
                    "Google 로그인이 설정되지 않았습니다.");
        }
        try {
            GoogleIdToken verified = verifier.verify(idToken);
            if (verified == null) throw invalidToken();
            GoogleIdToken.Payload payload = verified.getPayload();
            return new VerifiedGoogleIdentity(payload.getSubject(), payload.getEmail(),
                    Boolean.TRUE.equals(payload.getEmailVerified()), (String) payload.get("name"),
                    (String) payload.get("picture"));
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "GOOGLE_VERIFICATION_UNAVAILABLE",
                    "Google 인증 정보를 확인할 수 없습니다.");
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw invalidToken();
        }
    }

    private ApiException invalidToken() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_GOOGLE_TOKEN", "Google ID token을 검증할 수 없습니다.");
    }
}
