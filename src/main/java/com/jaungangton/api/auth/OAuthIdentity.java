package com.jaungangton.api.auth;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "oauth_identities")
public class OAuthIdentity {
    @Id
    private UUID id;
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Column(nullable = false, length = 32)
    private String provider;
    @Column(name = "provider_subject", nullable = false, length = 255)
    private String providerSubject;
    @Column(name = "provider_email", nullable = false, length = 320)
    private String providerEmail;
    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected OAuthIdentity() {
    }

    OAuthIdentity(UUID id, UUID userId, String providerSubject, String providerEmail, Instant createdAt) {
        this.id = id;
        this.userId = userId;
        this.provider = "GOOGLE";
        this.providerSubject = providerSubject;
        this.providerEmail = providerEmail;
        this.emailVerified = true;
        this.createdAt = createdAt;
    }

    public UUID getUserId() { return userId; }
}
