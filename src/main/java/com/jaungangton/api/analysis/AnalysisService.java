package com.jaungangton.api.analysis;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jaungangton.api.common.ApiException;
import com.jaungangton.api.recommendation.RecommendationQueryService;
import com.jaungangton.api.survey.Survey;
import com.jaungangton.api.survey.SurveyNumericSnapshot;
import com.jaungangton.api.survey.SurveyService;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class AnalysisService {
    private final AnalysisJobRepository repository;
    private final SurveyService surveyService;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final RecommendationQueryService recommendationQueryService;
    private final PhotoStoragePort photoStorage;
    private final PhotoAttachmentPersistenceService photoAttachmentPersistence;

    @Autowired
    public AnalysisService(
            AnalysisJobRepository repository,
            SurveyService surveyService,
            ObjectMapper objectMapper,
            RecommendationQueryService recommendationQueryService,
            PhotoStoragePort photoStorage,
            PhotoAttachmentPersistenceService photoAttachmentPersistence) {
        this(repository, surveyService, objectMapper, Clock.systemUTC(), recommendationQueryService,
                photoStorage, photoAttachmentPersistence);
    }

    AnalysisService(AnalysisJobRepository repository, SurveyService surveyService, ObjectMapper objectMapper, Clock clock) {
        this(repository, surveyService, objectMapper, clock, null, new DatabasePhotoStorageAdapter(),
                new PhotoAttachmentPersistenceService(repository, null, clock));
    }

    AnalysisService(
            AnalysisJobRepository repository,
            SurveyService surveyService,
            ObjectMapper objectMapper,
            Clock clock,
            RecommendationQueryService recommendationQueryService,
            PhotoStoragePort photoStorage) {
        this(repository, surveyService, objectMapper, clock, recommendationQueryService, photoStorage,
                new PhotoAttachmentPersistenceService(repository, recommendationQueryService, clock));
    }

    AnalysisService(
            AnalysisJobRepository repository,
            SurveyService surveyService,
            ObjectMapper objectMapper,
            Clock clock,
            RecommendationQueryService recommendationQueryService,
            PhotoStoragePort photoStorage,
            PhotoAttachmentPersistenceService photoAttachmentPersistence) {
        this.repository = repository;
        this.surveyService = surveyService;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.recommendationQueryService = recommendationQueryService;
        this.photoStorage = photoStorage;
        this.photoAttachmentPersistence = photoAttachmentPersistence;
    }

    @Transactional
    public AnalysisResponse create(UUID userId, String idempotencyKey, CreateAnalysisRequest request) {
        String normalizedKey = requireIdempotencyKey(idempotencyKey);
        return repository.findByUserIdAndIdempotencyKey(userId, normalizedKey)
                .map(job -> AnalysisResponse.from(job, recommendationId(job.id())))
                .orElseGet(() -> createNew(userId, normalizedKey, request));
    }

    @Transactional(readOnly = true)
    public AnalysisResponse get(UUID userId, UUID analysisId) {
        return repository.findByIdAndUserId(analysisId, userId)
                .map(job -> AnalysisResponse.from(job, recommendationId(job.id())))
                .orElseThrow(() -> notFound());
    }

    public PhotoUploadResult attachPhoto(UUID userId, UUID analysisId, byte[] photoData, String contentType) {
        PhotoStorageReference stored;
        try {
            stored = photoStorage.store(analysisId, photoData, contentType);
        } catch (PhotoStorageException exception) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PHOTO_STORAGE_UNAVAILABLE",
                    "사진 저장소를 사용할 수 없습니다.");
        }
        try {
            PhotoUploadResult result = photoAttachmentPersistence.attach(userId, analysisId, stored);
            if (!result.started()) {
                deleteBestEffort(stored);
            }
            return result;
        } catch (RuntimeException exception) {
            deleteBestEffort(stored);
            throw exception;
        }
    }

    public PhotoAnalysisInput photoInput(UUID analysisId) {
        AnalysisJob job = repository.findById(analysisId).orElseThrow(this::notFound);
        PhotoStorageReference reference = job.photoReference();
        if (reference == null || job.photoContentType() == null) {
            throw new ApiException(HttpStatus.CONFLICT, "PHOTO_REQUIRED", "사진 업로드가 필요합니다.");
        }
        return new PhotoAnalysisInput(
                job.id(), job.surveySnapshot(), photoStorage.load(reference), job.photoContentType());
    }

    private AnalysisResponse createNew(UUID userId, String idempotencyKey, CreateAnalysisRequest request) {
        Survey survey = surveyService.requireEntity(userId);
        SurveyNumericSnapshot snapshot = surveyService.numericSnapshot(survey);
        AnalysisJob job = new AnalysisJob(
                UUID.randomUUID(),
                userId,
                survey.id(),
                writeSnapshot(snapshot),
                request.budgetTotal(),
                idempotencyKey,
                Instant.now(clock));
        return AnalysisResponse.from(repository.save(job));
    }

    private String writeSnapshot(SurveyNumericSnapshot snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Could not serialize the survey snapshot", exception);
        }
    }

    private String requireIdempotencyKey(String value) {
        if (value == null || value.isBlank() || value.length() > 200) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_IDEMPOTENCY_KEY",
                    "Idempotency-Key must contain 1 to 200 characters.");
        }
        return value.trim();
    }

    private ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "ANALYSIS_NOT_FOUND", "분석 작업을 찾을 수 없습니다.");
    }

    private UUID recommendationId(UUID analysisId) {
        if (recommendationQueryService == null) {
            return null;
        }
        return recommendationQueryService.findByAnalysisId(analysisId)
                .map(response -> response.id())
                .orElse(null);
    }

    private void deleteBestEffort(PhotoStorageReference stored) {
        try {
            photoStorage.delete(stored);
        } catch (PhotoStorageException ignored) {
            // S3 lifecycle expiration is the final fallback for an unreferenced upload.
        }
    }
}
