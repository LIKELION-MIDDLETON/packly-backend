package com.jaungangton.api.ai;

import tools.jackson.databind.JsonNode;

public record AiRecommendationRequest(
        JsonNode cnnResult,
        JsonNode llmResult,
        JsonNode survey,
        Long budgetTotal,
        double alpha) {
}
