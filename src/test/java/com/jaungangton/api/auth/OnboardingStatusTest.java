package com.jaungangton.api.auth;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class OnboardingStatusTest {
    @Test
    void onlyExplicitNextTransitionsAreAllowed() {
        User user = new User(UUID.randomUUID(), "person@example.com", "Person", null, Instant.EPOCH);
        user.advanceOnboarding(OnboardingStatus.SURVEY_REQUIRED, Instant.EPOCH);
        user.advanceOnboarding(OnboardingStatus.PHOTO_REQUIRED, Instant.EPOCH);

        assertThatThrownBy(() -> user.advanceOnboarding(OnboardingStatus.COMPLETED, Instant.EPOCH))
                .isInstanceOf(IllegalStateException.class);
    }
}
