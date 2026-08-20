package com.jaungangton.api.analysis;

import java.util.UUID;

import tools.jackson.databind.JsonNode;

public record RecommendationWork(
        UUID analysisId,
        UUID userId,
        String surveySnapshot,
        Long budgetTotal,
        String sourceResultId,
        JsonNode cnnResult,
        JsonNode llmResult,
        JsonNode survey,
        boolean duplicate) {
    public RecommendationWork(
            UUID analysisId,
            UUID userId,
            String surveySnapshot,
            Long budgetTotal,
            String sourceResultId,
            JsonNode cnnResult,
            JsonNode llmResult,
            boolean duplicate) {
        this(analysisId, userId, surveySnapshot, budgetTotal, sourceResultId, cnnResult, llmResult, null, duplicate);
    }
}
