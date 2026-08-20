package com.jaungangton.api.engagement;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface RecommendationFeedbackRepository extends JpaRepository<RecommendationFeedback, UUID> {
    Optional<RecommendationFeedback> findByUserIdAndRecommendationId(UUID userId, UUID recommendationId);
}
