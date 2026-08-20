package com.jaungangton.api.recommendation;

import java.util.Optional;

import org.springframework.stereotype.Service;

import com.jaungangton.api.ai.AiRecommendationException;
import com.jaungangton.api.ai.AiRecommendationExchange;
import com.jaungangton.api.ai.AiRecommendationPort;
import com.jaungangton.api.ai.AiRecommendationRequest;
import com.jaungangton.api.analysis.RecommendationWork;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Coordinates the external call without holding a database transaction. */
@Service
public class RecommendationWorkflowService {
    private final AiRecommendationPort aiPort;
    private final RecommendationCompletionService completionService;
    private final RecommendationFailureService failureService;
    private final RecommendationQueryService queryService;
    private final ObjectMapper objectMapper;

    public RecommendationWorkflowService(
            AiRecommendationPort aiPort,
            RecommendationCompletionService completionService,
            RecommendationFailureService failureService,
            RecommendationQueryService queryService,
            ObjectMapper objectMapper) {
        this.aiPort = aiPort;
        this.completionService = completionService;
        this.failureService = failureService;
        this.queryService = queryService;
        this.objectMapper = objectMapper;
    }

    public Optional<RecommendationResultResponse> process(RecommendationWork work) {
        if (work.duplicate()) {
            return queryService.findByAnalysisId(work.analysisId());
        }
        try {
            JsonNode survey = work.survey() == null
                    ? objectMapper.readTree(work.surveySnapshot())
                    : work.survey();
            AiRecommendationRequest request = new AiRecommendationRequest(
                    work.cnnResult(), nullable(work.llmResult()), survey, work.budgetTotal(), 0.6);
            AiRecommendationExchange exchange = aiPort.recommend(request);
            return Optional.of(completionService.complete(work, exchange));
        } catch (JacksonException exception) {
            AiRecommendationException failure = new AiRecommendationException(
                    "AI_INVALID_REQUEST", "Stored survey snapshot is invalid", exception);
            failureService.fail(work.analysisId(), failure.failureCode());
            throw failure;
        } catch (AiRecommendationException exception) {
            failureService.fail(work.analysisId(), exception.failureCode());
            throw exception;
        }
    }

    private JsonNode nullable(JsonNode value) {
        return value == null || value.isNull() ? null : value;
    }
}
