package com.jaungangton.api.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.jaungangton.api.ai.AiRecommendationException;
import com.jaungangton.api.ai.AiRecommendationExchange;
import com.jaungangton.api.ai.AiRecommendationPort;
import com.jaungangton.api.ai.AiRecommendationResult;
import com.jaungangton.api.analysis.RecommendationWork;

import tools.jackson.databind.ObjectMapper;

class RecommendationWorkflowServiceTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void callsExternalAiWithoutAWorkflowTransactionThenCompletesInSeparateService() {
        AtomicBoolean transactionActiveDuringCall = new AtomicBoolean(true);
        AiRecommendationExchange exchange = exchange();
        AiRecommendationPort port = request -> {
            transactionActiveDuringCall.set(TransactionSynchronizationManager.isActualTransactionActive());
            return exchange;
        };
        RecommendationCompletionService completion = mock(RecommendationCompletionService.class);
        RecommendationFailureService failure = mock(RecommendationFailureService.class);
        RecommendationQueryService query = mock(RecommendationQueryService.class);
        RecommendationResultResponse expected = response();
        when(completion.complete(any(), any())).thenReturn(expected);
        RecommendationWorkflowService service = new RecommendationWorkflowService(
                port, completion, failure, query, objectMapper);

        Optional<RecommendationResultResponse> result = service.process(work(false));

        assertThat(result).contains(expected);
        assertThat(transactionActiveDuringCall).isFalse();
        assertThat(RecommendationWorkflowService.class.getAnnotation(Transactional.class)).isNull();
        verify(completion).complete(any(), any());
        verify(failure, never()).fail(any(), any());
    }

    @Test
    void recordsTypedAiFailureWithoutCreatingRecommendation() {
        AiRecommendationPort port = request -> {
            throw new AiRecommendationException("AI_TIMEOUT", "timed out");
        };
        RecommendationCompletionService completion = mock(RecommendationCompletionService.class);
        RecommendationFailureService failure = mock(RecommendationFailureService.class);
        RecommendationWorkflowService service = new RecommendationWorkflowService(
                port, completion, failure, mock(RecommendationQueryService.class), objectMapper);

        assertThatThrownBy(() -> service.process(work(false)))
                .isInstanceOf(AiRecommendationException.class);

        verify(failure).fail(work(false).analysisId(), "AI_TIMEOUT");
        verify(completion, never()).complete(any(), any());
    }

    @Test
    void duplicateCallbackNeverCallsAiAndReturnsExistingRecommendationWhenAvailable() {
        AiRecommendationPort port = mock(AiRecommendationPort.class);
        RecommendationQueryService query = mock(RecommendationQueryService.class);
        RecommendationResultResponse expected = response();
        RecommendationWork work = work(true);
        when(query.findByAnalysisId(work.analysisId())).thenReturn(Optional.of(expected));
        RecommendationWorkflowService service = new RecommendationWorkflowService(
                port, mock(RecommendationCompletionService.class), mock(RecommendationFailureService.class),
                query, objectMapper);

        assertThat(service.process(work)).contains(expected);
        verify(port, never()).recommend(any());
    }

    private RecommendationWork work(boolean duplicate) {
        return new RecommendationWork(
                UUID.fromString("10000000-0000-0000-0000-000000000001"),
                UUID.fromString("20000000-0000-0000-0000-000000000002"),
                "{\"skinType\":1,\"concerns\":[4],\"duration\":2,\"areas\":[3],\"irritation\":1,\"diagnosed\":1}",
                50_000L,
                "cnn-source-1",
                objectMapper.readTree("{\"predicted_label\":\"normal\",\"confidence\":0.9}"),
                null,
                duplicate);
    }

    private AiRecommendationExchange exchange() {
        AiRecommendationResult result = new AiRecommendationResult(
                "normal", "headline", "summary", 0.9, "normal", false, List.of(), null,
                List.of(), 0, null, List.of(), null);
        return new AiRecommendationExchange(result, "{}", "{}");
    }

    private RecommendationResultResponse response() {
        return new RecommendationResultResponse(
                UUID.randomUUID(), work(false).analysisId(), Instant.parse("2026-08-19T00:00:00Z"),
                "normal", "headline", "summary", 0.9, "normal",
                new MedicalAdviceResponse(false, List.of()), null, List.of(), 0L, null, List.of(), null,
                List.of(), null);
    }
}
