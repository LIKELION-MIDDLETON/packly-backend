package com.jaungangton.api.ai;

import tools.jackson.databind.JsonNode;

public record AiPhotoAnalysisExchange(
        JsonNode cnnResult,
        JsonNode llmResult,
        JsonNode survey,
        String responseSnapshot) {
}
