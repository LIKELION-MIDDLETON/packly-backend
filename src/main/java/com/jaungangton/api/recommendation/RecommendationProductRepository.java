package com.jaungangton.api.recommendation;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RecommendationProductRepository extends JpaRepository<RecommendationProduct, UUID> {
    Optional<RecommendationProduct> findByIdAndRecommendationUserId(UUID id, UUID userId);
}
