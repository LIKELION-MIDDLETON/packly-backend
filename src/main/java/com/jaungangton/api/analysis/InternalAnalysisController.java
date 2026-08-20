package com.jaungangton.api.analysis;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;
import java.util.UUID;

import com.jaungangton.api.common.ApiException;
import com.jaungangton.api.recommendation.RecommendationResultResponse;
import com.jaungangton.api.recommendation.RecommendationWorkflowService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/v1/internal/analyses")
public class InternalAnalysisController {
    private final AnalysisResultAcceptanceService acceptance;
    private final RecommendationWorkflowService workflow;
    private final String callbackKey;

    public InternalAnalysisController(
            AnalysisResultAcceptanceService acceptance,
            RecommendationWorkflowService workflow,
            @Value("${centralton.internal.callback-key:}") String callbackKey) {
        this.acceptance = acceptance;
        this.workflow = workflow;
        this.callbackKey = callbackKey;
    }

    @PostMapping("/{analysisId}/cnn-result")
    ResponseEntity<?> accept(
            @PathVariable UUID analysisId,
            @RequestHeader(value = "X-Internal-Callback-Key", required = false) String suppliedKey,
            @Valid @RequestBody CnnCallbackRequest request) {
        verifyKey(suppliedKey);
        RecommendationWork work = acceptance.acceptPhotoAnalysisResult(analysisId,
                new PhotoAnalysisResult(request.sourceResultId(), request.cnnResult(), request.llmResult()));
        Optional<RecommendationResultResponse> result = workflow.process(work);
        return result.<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.accepted().build());
    }

    private void verifyKey(String suppliedKey) {
        if (callbackKey.isBlank()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "INTERNAL_CALLBACK_NOT_CONFIGURED",
                    "내부 분석 연동이 설정되지 않았습니다.");
        }
        boolean matches = suppliedKey != null && !suppliedKey.isBlank() && MessageDigest.isEqual(
                callbackKey.getBytes(StandardCharsets.UTF_8),
                suppliedKey.getBytes(StandardCharsets.UTF_8));
        if (!matches) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_INTERNAL_CREDENTIAL",
                    "내부 인증 정보가 올바르지 않습니다.");
        }
    }

    public record CnnCallbackRequest(
            @NotBlank String sourceResultId,
            @NotNull JsonNode cnnResult,
            JsonNode llmResult) {
    }
}
