package com.jaungangton.api.engagement;

import java.time.Instant;
import java.util.UUID;

public record FeedbackResponse(
        UUID id,
        UUID recommendationId,
        int rating,
        String comment,
        Instant createdAt,
        Instant updatedAt
) {
    static FeedbackResponse from(RecommendationFeedback feedback) {
        return new FeedbackResponse(
                feedback.id(), feedback.recommendationId(), feedback.rating(), feedback.comment(),
                feedback.createdAt(), feedback.updatedAt());
    }
}
