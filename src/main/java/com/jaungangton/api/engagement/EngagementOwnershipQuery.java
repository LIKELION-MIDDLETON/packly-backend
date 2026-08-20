package com.jaungangton.api.engagement;

import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.springframework.stereotype.Repository;

@Repository
class EngagementOwnershipQuery {
    private final EntityManager entityManager;

    EngagementOwnershipQuery(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    boolean ownsRecommendation(UUID userId, UUID recommendationId) {
        Number count = (Number) entityManager.createNativeQuery("""
                SELECT COUNT(*) FROM recommendations
                WHERE id = :recommendationId AND user_id = :userId
                """)
                .setParameter("recommendationId", recommendationId)
                .setParameter("userId", userId)
                .getSingleResult();
        return count.longValue() > 0;
    }

    boolean ownsProduct(UUID userId, UUID recommendationId, UUID productId) {
        Number count = (Number) entityManager.createNativeQuery("""
                SELECT COUNT(*)
                FROM recommendation_products product
                JOIN recommendations recommendation ON recommendation.id = product.recommendation_id
                WHERE product.id = :productId
                  AND recommendation.id = :recommendationId
                  AND recommendation.user_id = :userId
                """)
                .setParameter("productId", productId)
                .setParameter("recommendationId", recommendationId)
                .setParameter("userId", userId)
                .getSingleResult();
        return count.longValue() > 0;
    }
}
