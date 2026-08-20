package com.jaungangton.api.auth;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "refresh_sessions")
public class RefreshSession {
    @Id
    private UUID id;
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Column(name = "family_id", nullable = false)
    private UUID familyId;
    @Column(name = "session_id", nullable = false)
    private UUID sessionId;
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    @Column(name = "used_at")
    private Instant usedAt;
    @Column(name = "revoked_at")
    private Instant revokedAt;
    @Column(name = "replaced_by")
    private UUID replacedBy;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Version
    private long version;

    protected RefreshSession() {
    }

    RefreshSession(UUID id, UUID userId, UUID familyId, UUID sessionId, String tokenHash,
                   Instant expiresAt, Instant createdAt) {
        this.id = id;
        this.userId = userId;
        this.familyId = familyId;
        this.sessionId = sessionId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
    }

    void rotateTo(UUID replacementId, Instant now) {
        this.usedAt = now;
        this.replacedBy = replacementId;
    }

    void revoke(Instant now) {
        if (this.revokedAt == null) this.revokedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public UUID getFamilyId() { return familyId; }
    public UUID getSessionId() { return sessionId; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getUsedAt() { return usedAt; }
    public Instant getRevokedAt() { return revokedAt; }
}
