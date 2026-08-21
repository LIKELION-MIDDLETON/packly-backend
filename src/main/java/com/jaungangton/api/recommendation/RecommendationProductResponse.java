package com.jaungangton.api.recommendation;

import java.util.UUID;

import tools.jackson.databind.JsonNode;

public record RecommendationProductResponse(
        UUID id,
        int order,
        int displayOrder,
        Integer applicationOrder,
        String usageGroup,
        String slot,
        String goodsNo,
        String brand,
        String name,
        /** Deprecated purchase-price alias; never a daily price. */
        Long price,
        Long dailyPrice,
        String dailyVolume,
        String totalVolume,
        Long salePrice,
        String recommendationReason,
        JsonNode suitability,
        String suitabilitySource,
        String functionalInfo,
        boolean unscented,
        int comedogenicScore,
        String productUrl,
        String imageUrl) {
}
