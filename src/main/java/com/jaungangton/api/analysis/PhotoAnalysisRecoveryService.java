package com.jaungangton.api.analysis;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

import com.jaungangton.api.recommendation.RecommendationWorkflowService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Resumes photo-analysis and recommendation jobs that were left in flight when this process stopped.
 *
 * The submission guard is intentionally JVM-local. It prevents duplicate work when the ready
 * event is published more than once during one process lifetime, but it does not coordinate
 * multiple application instances. A durable claim or lease is required before scaling this
 * recovery across instances; the current deployment assumes one EC2 application instance.
 */
@Component
final class PhotoAnalysisRecoveryService {
    static final String RECOVERY_INPUT_MISSING = "RECOVERY_INPUT_MISSING";
    static final String RECOVERY_INPUT_INVALID = "RECOVERY_INPUT_INVALID";
    private static final Logger log = LoggerFactory.getLogger(PhotoAnalysisRecoveryService.class);

    private final AnalysisJobRepository repository;
    private final PhotoAnalysisWorkflowService photoWorkflow;
    private final RecommendationWorkflowService recommendationWorkflow;
    private final AnalysisResultAcceptanceService acceptanceService;
    private final ObjectMapper objectMapper;
    private final Executor executor;
    private final Set<UUID> submitted = ConcurrentHashMap.newKeySet();

    PhotoAnalysisRecoveryService(
            AnalysisJobRepository repository,
            PhotoAnalysisWorkflowService photoWorkflow,
            RecommendationWorkflowService recommendationWorkflow,
            AnalysisResultAcceptanceService acceptanceService,
            ObjectMapper objectMapper,
            @Qualifier("photoAnalysisExecutor") Executor executor) {
        this.repository = repository;
        this.photoWorkflow = photoWorkflow;
        this.recommendationWorkflow = recommendationWorkflow;
        this.acceptanceService = acceptanceService;
        this.objectMapper = objectMapper;
        this.executor = executor;
    }

    @EventListener(ApplicationReadyEvent.class)
    void recoverInFlightAnalyses(ApplicationReadyEvent ignored) {
        for (AnalysisJob job : repository.findRecoveryCandidates(
                AnalysisStatus.ANALYZING, AnalysisStatus.RECOMMENDING)) {
            if (!isCandidate(job)) {
                continue;
            }
            UUID analysisId = job.id();
            if (!submitted.add(analysisId)) {
                continue;
            }
            try {
                executor.execute(() -> recoverSafely(analysisId));
            } catch (RuntimeException exception) {
                submitted.remove(analysisId);
                log.error("Could not resubmit photo analysis {} after application startup", analysisId, exception);
            }
        }
    }

    @Scheduled(
            initialDelayString = "${centralton.analysis.recovery-scan-delay-ms:300000}",
            fixedDelayString = "${centralton.analysis.recovery-scan-delay-ms:300000}")
    void retryInFlightAnalyses() {
        recoverInFlightAnalyses(null);
    }

    private boolean isCandidate(AnalysisJob job) {
        return (job.status() == AnalysisStatus.ANALYZING && job.hasPhoto())
                || job.status() == AnalysisStatus.RECOMMENDING;
    }

    private void recoverSafely(UUID analysisId) {
        try {
            AnalysisJob job = repository.findById(analysisId).orElse(null);
            if (job == null) {
                return;
            }
            if (job.status() == AnalysisStatus.ANALYZING) {
                if (job.hasPhoto()) {
                    photoWorkflow.process(analysisId);
                }
                return;
            }
            if (job.status() == AnalysisStatus.RECOMMENDING) {
                recoverRecommendation(job);
            }
        } catch (JacksonException exception) {
            failSafely(analysisId, RECOVERY_INPUT_INVALID, exception);
        } catch (RuntimeException exception) {
            failSafely(analysisId, "RECOVERY_FAILED", exception);
        } finally {
            submitted.remove(analysisId);
        }
    }

    private void recoverRecommendation(AnalysisJob job) throws JacksonException {
        if (job.sourceResultId() == null || job.sourceResultId().isBlank()
                || !job.hasRecommendationInputSnapshot()) {
            acceptanceService.markFailed(job.id(), RECOVERY_INPUT_MISSING);
            return;
        }

        JsonNode cnnResult = objectMapper.readTree(job.cnnResultJson());
        if (cnnResult == null || cnnResult.isNull()) {
            acceptanceService.markFailed(job.id(), RECOVERY_INPUT_MISSING);
            return;
        }
        recommendationWorkflow.process(new RecommendationWork(
                job.id(),
                job.userId(),
                job.surveySnapshot(),
                job.budgetTotal(),
                job.sourceResultId(),
                cnnResult,
                readOptional(job.llmResultJson()),
                readOptional(job.surveyResultJson()),
                false));
    }

    private JsonNode readOptional(String value) throws JacksonException {
        if (value == null || value.isBlank()) {
            return null;
        }
        JsonNode node = objectMapper.readTree(value);
        return node == null || node.isNull() ? null : node;
    }

    private void failSafely(UUID analysisId, String failureCode, Exception cause) {
        try {
            acceptanceService.markFailed(analysisId, failureCode);
        } catch (RuntimeException exception) {
            log.error("Could not mark recovered analysis {} as {}", analysisId, failureCode, exception);
        }
        log.error("Invalid recovery input for analysis {}", analysisId, cause);
    }
}
