package com.jaungangton.api.analysis;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.jaungangton.api.common.ApiException;
import com.jaungangton.api.auth.OnboardingStatus;
import com.jaungangton.api.auth.UserOnboardingService;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class AnalysisResultAcceptanceService {
    private final AnalysisJobRepository repository;
    private final Clock clock;
    private final UserOnboardingService onboarding;
    private final PhotoStoragePort photoStorage;
    private final ObjectMapper objectMapper;

    @Autowired
    public AnalysisResultAcceptanceService(
            AnalysisJobRepository repository,
            UserOnboardingService onboarding,
            PhotoStoragePort photoStorage,
            ObjectMapper objectMapper) {
        this(repository, onboarding, Clock.systemUTC(), photoStorage, objectMapper);
    }

    AnalysisResultAcceptanceService(AnalysisJobRepository repository, UserOnboardingService onboarding, Clock clock) {
        this(repository, onboarding, clock, new DatabasePhotoStorageAdapter(), new ObjectMapper());
    }

    AnalysisResultAcceptanceService(AnalysisJobRepository repository, Clock clock) {
        this(repository, null, clock, new DatabasePhotoStorageAdapter(), new ObjectMapper());
    }

    AnalysisResultAcceptanceService(
            AnalysisJobRepository repository,
            UserOnboardingService onboarding,
            Clock clock,
            PhotoStoragePort photoStorage) {
        this(repository, onboarding, clock, photoStorage, new ObjectMapper());
    }

    AnalysisResultAcceptanceService(
            AnalysisJobRepository repository,
            UserOnboardingService onboarding,
            Clock clock,
            PhotoStoragePort photoStorage,
            ObjectMapper objectMapper) {
        this.repository = repository;
        this.onboarding = onboarding;
        this.clock = clock;
        this.photoStorage = photoStorage;
        this.objectMapper = objectMapper;
    }

    /**
     * Trusted integration entry point. Callers must authenticate the provider before
     * invoking this method and must skip AI work when the returned work is duplicate.
     */
    @Transactional
    public RecommendationWork acceptPhotoAnalysisResult(UUID analysisId, PhotoAnalysisResult result) {
        validate(result);
        AnalysisJob sameSource = repository.findBySourceResultId(result.sourceResultId()).orElse(null);
        if (sameSource != null) {
            if (!sameSource.id().equals(analysisId)) {
                throw new ApiException(HttpStatus.CONFLICT, "SOURCE_RESULT_ALREADY_USED",
                        "The photo analysis result has already been used.");
            }
            return work(sameSource, result, true);
        }

        AnalysisJob job = repository.findForUpdateById(analysisId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ANALYSIS_NOT_FOUND",
                        "분석 작업을 찾을 수 없습니다."));
        if (result.sourceResultId().equals(job.sourceResultId())) {
            return work(job, result, true);
        }
        String cnnResultJson = json(result.cnnResult());
        String llmResultJson = json(result.llmResult());
        String surveyResultJson = json(result.survey());
        try {
            job.acceptPhotoResult(
                    result.sourceResultId(),
                    cnnResultJson,
                    llmResultJson,
                    surveyResultJson,
                    Instant.now(clock));
        } catch (IllegalStateException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_ANALYSIS_STATE",
                    "The analysis cannot accept a photo result in its current state.");
        }
        repository.save(job);
        if (onboarding != null) onboarding.advance(job.userId(), OnboardingStatus.RECOMMENDATION_PENDING);
        return work(job, result, false);
    }

    @Transactional
    public void markCompleted(UUID analysisId) {
        AnalysisJob job = require(analysisId);
        try {
            job.complete(Instant.now(clock));
            cleanupPhoto(job);
            if (onboarding != null) onboarding.advance(job.userId(), OnboardingStatus.COMPLETED);
        } catch (IllegalStateException exception) {
            throw invalidState();
        }
    }

    @Transactional
    public void markFailed(UUID analysisId, String failureCode) {
        if (failureCode == null || failureCode.isBlank() || failureCode.length() > 64) {
            throw new IllegalArgumentException("failureCode must contain 1 to 64 characters");
        }
        AnalysisJob job = require(analysisId);
        try {
            job.fail(failureCode, Instant.now(clock));
            cleanupPhoto(job);
        } catch (IllegalStateException exception) {
            throw invalidState();
        }
    }

    private AnalysisJob require(UUID analysisId) {
        return repository.findById(analysisId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ANALYSIS_NOT_FOUND",
                        "분석 작업을 찾을 수 없습다."));
    }

    private RecommendationWork work(AnalysisJob job, PhotoAnalysisResult result, boolean duplicate) {
        return new RecommendationWork(
                job.id(), job.userId(), job.surveySnapshot(), job.budgetTotal(),
                result.sourceResultId(), result.cnnResult(), result.llmResult(), result.survey(), duplicate);
    }

    private void validate(PhotoAnalysisResult result) {
        if (result == null || result.sourceResultId() == null || result.sourceResultId().isBlank()
                || result.sourceResultId().length() > 255 || result.cnnResult() == null || result.cnnResult().isNull()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PHOTO_ANALYSIS_RESULT",
                    "sourceResultId and cnnResult are required.");
        }
    }

    private String json(JsonNode value) {
        if (value == null || value.isNull()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Could not persist photo analysis JSON", exception);
        }
    }

    private ApiException invalidState() {
        return new ApiException(HttpStatus.CONFLICT, "INVALID_ANALYSIS_STATE",
                "The analysis cannot transition from its current state.");
    }

    private void cleanupPhoto(AnalysisJob job) {
        PhotoStorageReference reference = job.photoReference();
        if (reference == null) {
            return;
        }
        job.clearPhoto();
        deleteAfterCommit(reference);
    }

    private void deleteAfterCommit(PhotoStorageReference reference) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    deleteBestEffort(reference);
                }
            });
            return;
        }
        deleteBestEffort(reference);
    }

    private void deleteBestEffort(PhotoStorageReference reference) {
        try {
            photoStorage.delete(reference);
        } catch (PhotoStorageException ignored) {
            // The terminal-state lifecycle rule is the final fallback for failed object cleanup.
        }
    }
}
