package com.jaungangton.api.analysis;

import tools.jackson.databind.JsonNode;

/** Result accepted only from a trusted internal integration, never from a user API. */
public record PhotoAnalysisResult(
        String sourceResultId,
        JsonNode cnnResult,
        JsonNode llmResult,
        JsonNode survey) {
    public PhotoAnalysisResult(String sourceResultId, JsonNode cnnResult, JsonNode llmResult) {
        this(sourceResultId, cnnResult, llmResult, null);
    }
}
