package com.jaungangton.api.ai;

import tools.jackson.databind.JsonNode;

public record AiRecommendationProduct(
        int displayOrder,
        Integer applicationOrder,
        String usageGroup,
        String slot,
        String goodsNo,
        String brand,
        String name,
        /** Deprecated purchase-price alias. Never populated from dailyPrice. */
        Long price,
        JsonNode suitability,
        String suitabilitySource,
        String functionalInfo,
        boolean unscented,
        int comedogenicScore,
        Long dailyPrice,
        String dailyVolume,
        String totalVolume,
        Long salePrice,
        String recommendationReason) {

    public AiRecommendationProduct(
                int order,
                String slot,
                String goodsNo,
                String brand,
                String name,
            long price,
                JsonNode suitability,
                String suitabilitySource,
                String functionalInfo,
                boolean unscented,
                int comedogenicScore) {
        this(order, ProductSlotNormalizer.applicationOrder(order), ProductSlotNormalizer.usageGroup(order, slot),
                slot, goodsNo, brand, name, price, suitability, suitabilitySource, functionalInfo, unscented,
                comedogenicScore, null, null, null, null, null);
    }

    /** Deprecated compatibility alias for the former ambiguous order field. */
    public int order() {
        return displayOrder;
    }
}
