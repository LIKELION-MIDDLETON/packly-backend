package com.jaungangton.api.auth;

public interface GoogleTokenVerifierPort {
    VerifiedGoogleIdentity verify(String idToken);

    record VerifiedGoogleIdentity(String subject, String email, boolean emailVerified,
                                  String displayName, String avatarUrl) {
    }
}
