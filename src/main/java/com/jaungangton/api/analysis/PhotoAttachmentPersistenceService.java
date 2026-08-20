package com.jaungangton.api.analysis;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jaungangton.api.common.ApiException;
import com.jaungangton.api.recommendation.RecommendationQueryService;

/** Persists an already-uploaded photo reference without performing external storage I/O. */
@Service
class PhotoAttachmentPersistenceService {
    private final AnalysisJobRepository repository;
    private final RecommendationQueryService recommendationQueryService;
    private final Clock clock;

    PhotoAttachmentPersistenceService(
            AnalysisJobRepository repository,
            RecommendationQueryService recommendationQueryService,
            Clock clock) {
        this.repository = repository;
        this.recommendationQueryService = recommendationQueryService;
        this.clock = clock;
    }

    @Transactional
    PhotoUploadResult attach(UUID userId, UUID analysisId, PhotoStorageReference stored) {
        AnalysisJob job = repository.findForUpdateByIdAndUserId(analysisId, userId)
                .orElseThrow(this::notFound);
        if (job.status() != AnalysisStatus.WAITING_FOR_PHOTO_ANALYSIS) {
            if (job.status() == AnalysisStatus.COMPLETED
                    || (job.hasPhoto() && (job.status() == AnalysisStatus.ANALYZING
                    || job.status() == AnalysisStatus.RECOMMENDING))) {
                return new PhotoUploadResult(
                        AnalysisResponse.from(job, recommendationId(job.id())), false);
            }
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_ANALYSIS_STATE",
                    "The analysis cannot accept a photo in its current state.");
        }
        try {
            job.attachPhoto(stored, Instant.now(clock));
            repository.save(job);
        } catch (IllegalStateException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_ANALYSIS_STATE",
                    "The analysis cannot accept a photo in its current state.");
        }
        return new PhotoUploadResult(AnalysisResponse.from(job), true);
    }

    private UUID recommendationId(UUID analysisId) {
        if (recommendationQueryService == null) {
            return null;
        }
        return recommendationQueryService.findByAnalysisId(analysisId)
                .map(response -> response.id())
                .orElse(null);
    }

    private ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "ANALYSIS_NOT_FOUND", "분석 작업을 찾을 수 없습니다.");
    }
}
