package com.jaungangton.api.recommendation;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import tools.jackson.databind.JsonNode;

public record RecommendationResultResponse(
        UUID id,
        UUID analysisId,
        Instant createdAt,
        String diagnosis,
        String headline,
        String summary,
        double confidence,
        String triage,
        MedicalAdviceResponse medicalAdvice,
        JsonNode reflectedSurvey,
        List<RecommendationProductResponse> products,
        /** Deprecated purchase-total alias; null when the legacy total is unavailable. */
        Long totalPrice,
        Long totalPriceDaily,
        List<RecommendationPurchaseOptionResponse> purchaseOptions,
        String analysisSummary,
        List<String> careRecommendations,
        String disclaimer) {
}
