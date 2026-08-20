package com.jaungangton.api.engagement;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface ProductUsageCompletionRepository extends JpaRepository<ProductUsageCompletion, UUID> {
    Optional<ProductUsageCompletion> findByUserIdAndRecommendationProductIdAndUsedOn(
            UUID userId, UUID recommendationProductId, LocalDate usedOn);
}
