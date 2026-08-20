package com.jaungangton.api.auth;

public enum OnboardingStatus {
    PROFILE_REQUIRED,
    SURVEY_REQUIRED,
    PHOTO_REQUIRED,
    RECOMMENDATION_PENDING,
    COMPLETED;

    public boolean canTransitionTo(OnboardingStatus target) {
        return switch (this) {
            case PROFILE_REQUIRED -> target == SURVEY_REQUIRED;
            case SURVEY_REQUIRED -> target == PHOTO_REQUIRED;
            case PHOTO_REQUIRED -> target == RECOMMENDATION_PENDING;
            case RECOMMENDATION_PENDING -> target == COMPLETED;
            case COMPLETED -> false;
        };
    }
}
