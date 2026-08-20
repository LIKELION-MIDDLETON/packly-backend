package com.jaungangton.api.ai;

public record AiRecommendationExchange(
        AiRecommendationResult result,
        String requestSnapshot,
        String responseSnapshot) {
}
