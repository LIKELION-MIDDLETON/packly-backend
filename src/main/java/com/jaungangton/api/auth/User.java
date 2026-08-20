package com.jaungangton.api.auth;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class User {
    @Id
    private UUID id;
    @Column(name = "normalized_email", nullable = false, length = 320)
    private String normalizedEmail;
    @Column(nullable = false, length = 320)
    private String email;
    @Column(nullable = false, length = 100)
    private String name;
    @Column(name = "avatar_url", length = 2000)
    private String avatarUrl;
    @Column(length = 20)
    private String nickname;
    @Column(name = "normalized_nickname", length = 20, unique = true)
    private String normalizedNickname;
    @Column(name = "postal_code", length = 5)
    private String postalCode;
    @Column(name = "address_line1", length = 200)
    private String addressLine1;
    @Column(name = "address_line2", length = 200)
    private String addressLine2;
    @Enumerated(EnumType.STRING)
    @Column(name = "onboarding_status", nullable = false, length = 32)
    private OnboardingStatus onboardingStatus;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected User() {
    }

    User(UUID id, String email, String name, String avatarUrl, Instant now) {
        this.id = id;
        this.email = email;
        this.normalizedEmail = normalize(email);
        this.name = name;
        this.avatarUrl = avatarUrl;
        this.onboardingStatus = OnboardingStatus.PROFILE_REQUIRED;
        this.createdAt = now;
        this.updatedAt = now;
    }

    void updateGoogleProfile(String email, String name, String avatarUrl, Instant now) {
        this.email = email;
        this.normalizedEmail = normalize(email);
        this.name = name;
        this.avatarUrl = avatarUrl;
        this.updatedAt = now;
    }

    void updateProfile(String nickname, String postalCode, String addressLine1, String addressLine2, Instant now) {
        this.nickname = nickname.strip();
        this.normalizedNickname = normalizeNickname(this.nickname);
        this.postalCode = postalCode.strip();
        this.addressLine1 = addressLine1.strip();
        this.addressLine2 = addressLine2 == null || addressLine2.isBlank() ? null : addressLine2.strip();
        if (onboardingStatus == OnboardingStatus.PROFILE_REQUIRED) {
            onboardingStatus = OnboardingStatus.SURVEY_REQUIRED;
        }
        updatedAt = now;
    }

    void advanceOnboarding(OnboardingStatus target, Instant now) {
        if (target == onboardingStatus) return;
        if (onboardingStatus == OnboardingStatus.COMPLETED) return;
        if (!onboardingStatus.canTransitionTo(target)) {
            throw new IllegalStateException("Invalid onboarding transition: " + onboardingStatus + " -> " + target);
        }
        onboardingStatus = target;
        updatedAt = now;
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    static String normalizeNickname(String nickname) {
        return Pattern.compile("\\s+").matcher(nickname.strip().toLowerCase(Locale.ROOT)).replaceAll("");
    }

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getName() { return name; }
    public String getAvatarUrl() { return avatarUrl; }
    public String getNickname() { return nickname; }
    public String getPostalCode() { return postalCode; }
    public String getAddressLine1() { return addressLine1; }
    public String getAddressLine2() { return addressLine2; }
    public OnboardingStatus getOnboardingStatus() { return onboardingStatus; }
}
