package com.jaungangton.api.auth;

import com.jaungangton.api.common.ApiException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoogleIdTokenVerifierAdapterTest {
    @Test
    void malformedTokenIsRejectedAsAnInvalidGoogleToken() {
        GoogleIdTokenVerifierAdapter verifier = new GoogleIdTokenVerifierAdapter(
                "test-client.apps.googleusercontent.com");

        assertThatThrownBy(() -> verifier.verify("invalid-token"))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    org.assertj.core.api.Assertions.assertThat(exception.status().value()).isEqualTo(401);
                    org.assertj.core.api.Assertions.assertThat(exception.code()).isEqualTo("INVALID_GOOGLE_TOKEN");
                });
    }
}
