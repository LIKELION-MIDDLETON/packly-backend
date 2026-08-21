package com.jaungangton.api.recommendation;

public record RecommendationPurchaseOptionResponse(
        int durationDays,
        String label,
        Long totalPrice) {
}
