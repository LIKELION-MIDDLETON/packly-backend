package com.jaungangton.api.auth;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import com.jaungangton.api.auth.GoogleTokenVerifierPort.VerifiedGoogleIdentity;
import com.jaungangton.api.common.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AuthPersistenceService {
    private final UserRepository users;
    private final OAuthIdentityRepository identities;
    private final RefreshSessionRepository refreshSessions;
    private final OpaqueRefreshTokens refreshTokens;
    private final JwtService jwtService;
    private final Clock clock;
    private final long refreshTtlDays;

    AuthPersistenceService(UserRepository users, OAuthIdentityRepository identities,
                           RefreshSessionRepository refreshSessions, OpaqueRefreshTokens refreshTokens,
                           JwtService jwtService, Clock clock,
                           @Value("${centralton.jwt.refresh-ttl-days:30}") long refreshTtlDays) {
        this.users = users;
        this.identities = identities;
        this.refreshSessions = refreshSessions;
        this.refreshTokens = refreshTokens;
        this.jwtService = jwtService;
        this.clock = clock;
        this.refreshTtlDays = refreshTtlDays;
    }

    @Transactional
    AuthResult exchangeGoogle(VerifiedGoogleIdentity google, Boolean termsAccepted) {
        Instant now = clock.instant();
        OAuthIdentity identity = identities.findByProviderAndProviderSubject("GOOGLE", google.subject()).orElse(null);
        boolean isNewUser = identity == null;
        User user;
        if (isNewUser) {
            if (!Boolean.TRUE.equals(termsAccepted)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "TERMS_ACCEPTANCE_REQUIRED",
                        "신규 계정을 만들려면 약관 동의가 필요합니다.");
            }
            user = users.save(new User(UUID.randomUUID(), google.email(), displayName(google), google.avatarUrl(), now));
            identities.save(new OAuthIdentity(UUID.randomUUID(), user.getId(), google.subject(), google.email(), now));
        } else {
            user = users.findById(identity.getUserId())
                    .orElseThrow(() -> new IllegalStateException("OAuth identity has no user"));
            user.updateGoogleProfile(google.email(), displayName(google), google.avatarUrl(), now);
        }
        return issueNewFamily(user, isNewUser, now);
    }

    @Transactional(noRollbackFor = ApiException.class)
    AuthResult rotate(String rawRefreshToken) {
        Instant now = clock.instant();
        RefreshSession current = refreshSessions.findByTokenHashForUpdate(refreshTokens.hash(rawRefreshToken))
                .orElseThrow(this::invalidRefreshToken);
        if (current.getUsedAt() != null) {
            revokeFamily(current.getFamilyId(), now);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_REUSED",
                    "Refresh token 재사용이 감지되어 해당 세션을 폐기했습니다.");
        }
        if (current.getRevokedAt() != null || !current.getExpiresAt().isAfter(now)) {
            current.revoke(now);
            throw invalidRefreshToken();
        }

        User user = users.findById(current.getUserId()).orElseThrow(this::invalidRefreshToken);
        String replacementRaw = refreshTokens.generate();
        UUID replacementId = UUID.randomUUID();
        Instant replacementExpiry = current.getExpiresAt();
        RefreshSession replacement = new RefreshSession(replacementId, user.getId(), current.getFamilyId(),
                current.getSessionId(), refreshTokens.hash(replacementRaw), replacementExpiry, now);
        refreshSessions.save(replacement);
        current.rotateTo(replacementId, now);

        JwtService.IssuedAccessToken access = jwtService.issue(user.getId(), current.getSessionId());
        return new AuthResult(user, false, access.value(), access.expiresAt(), replacementRaw,
                replacementExpiry);
    }

    /**
     * Resolves the winner after a concurrent first exchange hit the provider-subject
     * unique constraint. This method runs in a fresh transaction after the failed
     * insert transaction has rolled back.
     */
    @Transactional
    AuthResult exchangeExistingGoogle(VerifiedGoogleIdentity google) {
        OAuthIdentity identity = identities
                .findByProviderAndProviderSubject("GOOGLE", google.subject())
                .orElse(null);
        if (identity == null) return null;
        Instant now = clock.instant();
        User user = users.findById(identity.getUserId())
                .orElseThrow(() -> new IllegalStateException("OAuth identity has no user"));
        user.updateGoogleProfile(google.email(), displayName(google), google.avatarUrl(), now);
        return issueNewFamily(user, false, now);
    }

    @Transactional(noRollbackFor = ApiException.class)
    void logout(UUID userId, String rawRefreshToken) {
        Instant now = clock.instant();
        RefreshSession session = refreshSessions.findByTokenHashForUpdate(refreshTokens.hash(rawRefreshToken))
                .orElseThrow(this::invalidRefreshToken);
        if (!session.getUserId().equals(userId)) throw invalidRefreshToken();
        revokeFamily(session.getFamilyId(), now);
    }

    @Transactional(readOnly = true)
    User requireUser(UUID userId) {
        return users.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "사용자를 찾을 수 없습니다."));
    }

    private AuthResult issueNewFamily(User user, boolean isNewUser, Instant now) {
        UUID familyId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        String rawRefresh = refreshTokens.generate();
        Instant refreshExpiresAt = now.plus(refreshTtlDays, ChronoUnit.DAYS);
        refreshSessions.save(new RefreshSession(UUID.randomUUID(), user.getId(), familyId, sessionId,
                refreshTokens.hash(rawRefresh), refreshExpiresAt, now));
        JwtService.IssuedAccessToken access = jwtService.issue(user.getId(), sessionId);
        return new AuthResult(user, isNewUser, access.value(), access.expiresAt(), rawRefresh,
                refreshExpiresAt);
    }

    private void revokeFamily(UUID familyId, Instant now) {
        refreshSessions.findFamilyForUpdate(familyId).forEach(session -> session.revoke(now));
    }

    private ApiException invalidRefreshToken() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "Refresh token이 유효하지 않습니다.");
    }

    private String displayName(VerifiedGoogleIdentity google) {
        if (google.displayName() != null && !google.displayName().isBlank()) return google.displayName();
        int at = google.email().indexOf('@');
        return at > 0 ? google.email().substring(0, at) : "Google User";
    }

    record AuthResult(User user, boolean isNewUser, String accessToken,
                      Instant accessTokenExpiresAt, String refreshToken, Instant refreshTokenExpiresAt) {
    }
}
