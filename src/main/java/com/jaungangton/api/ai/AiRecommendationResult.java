package com.jaungangton.api.ai;

import java.util.List;

import tools.jackson.databind.JsonNode;

public record AiRecommendationResult(
        String diagnosis,
        String headline,
        String summary,
        double confidence,
        String triage,
        boolean medicalRecommended,
        List<String> medicalReasons,
        JsonNode reflectedSurvey,
        List<AiRecommendationProduct> products,
        Long totalPrice,
        Long totalPriceDaily,
        String analysisSummary,
        List<String> careRecommendations,
        String disclaimer) {

    /** Legacy constructor for the pre-PR#9 total-price contract. */
    public AiRecommendationResult(
            String diagnosis,
            String headline,
            String summary,
            double confidence,
            String triage,
            boolean medicalRecommended,
            List<String> medicalReasons,
            JsonNode reflectedSurvey,
            List<AiRecommendationProduct> products,
            long totalPrice,
            String analysisSummary,
            List<String> careRecommendations,
            String disclaimer) {
        this(diagnosis, headline, summary, confidence, triage, medicalRecommended, medicalReasons,
                reflectedSurvey, products, totalPrice, null, analysisSummary, careRecommendations, disclaimer);
    }
}
