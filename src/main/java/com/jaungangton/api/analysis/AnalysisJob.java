package com.jaungangton.api.analysis;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "analysis_jobs")
public class AnalysisJob {
    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "survey_id", nullable = false)
    private UUID surveyId;

    @Column(name = "survey_snapshot", nullable = false, columnDefinition = "TEXT")
    private String surveySnapshot;

    @Column(name = "budget_total")
    private Long budgetTotal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AnalysisStatus status;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Column(name = "external_job_id")
    private String externalJobId;

    @Column(name = "source_result_id", unique = true)
    private String sourceResultId;

    @Column(name = "failure_code")
    private String failureCode;

    @Column(name = "cnn_result_json", columnDefinition = "TEXT")
    private String cnnResultJson;

    @Column(name = "llm_result_json", columnDefinition = "TEXT")
    private String llmResultJson;

    @Column(name = "survey_result_json", columnDefinition = "TEXT")
    private String surveyResultJson;

    @Column(name = "photo_data")
    private byte[] photoData;

    @Column(name = "photo_content_type", length = 64)
    private String photoContentType;

    @Column(name = "photo_object_key", length = 1024)
    private String photoObjectKey;

    @Column(name = "photo_size")
    private Long photoSize;

    @Column(name = "photo_checksum", length = 64)
    private String photoChecksum;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected AnalysisJob() {
    }

    AnalysisJob(UUID id, UUID userId, UUID surveyId, String surveySnapshot, Long budgetTotal,
            String idempotencyKey, Instant now) {
        this.id = id;
        this.userId = userId;
        this.surveyId = surveyId;
        this.surveySnapshot = surveySnapshot;
        this.budgetTotal = budgetTotal;
        this.idempotencyKey = idempotencyKey;
        this.status = AnalysisStatus.WAITING_FOR_PHOTO_ANALYSIS;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** Compatibility transition for legacy rows; new RECOMMENDING transitions use the snapshot overload. */
    void acceptPhotoResult(String sourceResultId, Instant now) {
        acceptPhotoResult(sourceResultId, null, null, null, now, false);
    }

    void acceptPhotoResult(
            String sourceResultId,
            String cnnResultJson,
            String llmResultJson,
            String surveyResultJson,
            Instant now) {
        acceptPhotoResult(sourceResultId, cnnResultJson, llmResultJson, surveyResultJson, now, true);
    }

    private void acceptPhotoResult(
            String sourceResultId,
            String cnnResultJson,
            String llmResultJson,
            String surveyResultJson,
            Instant now,
            boolean requireCnnSnapshot) {
        if (status != AnalysisStatus.WAITING_FOR_PHOTO_ANALYSIS && status != AnalysisStatus.ANALYZING) {
            throw new IllegalStateException("Analysis is not waiting for a photo result");
        }
        if (requireCnnSnapshot && (cnnResultJson == null || cnnResultJson.isBlank())) {
            throw new IllegalArgumentException("cnnResultJson is required for a recommending analysis");
        }
        this.sourceResultId = sourceResultId;
        this.cnnResultJson = cnnResultJson;
        this.llmResultJson = llmResultJson;
        this.surveyResultJson = surveyResultJson;
        this.status = AnalysisStatus.RECOMMENDING;
        this.failureCode = null;
        this.updatedAt = now;
    }

    void attachPhoto(PhotoStorageReference photo, Instant now) {
        if (status != AnalysisStatus.WAITING_FOR_PHOTO_ANALYSIS) {
            throw new IllegalStateException("Analysis is not waiting for a photo upload");
        }
        this.photoData = photo.databaseData();
        this.photoObjectKey = photo.objectKey();
        this.photoContentType = photo.contentType();
        this.photoSize = photo.size();
        this.photoChecksum = photo.checksum();
        this.status = AnalysisStatus.ANALYZING;
        this.failureCode = null;
        this.updatedAt = now;
    }

    void complete(Instant now) {
        if (status != AnalysisStatus.RECOMMENDING) {
            throw new IllegalStateException("Analysis is not recommending");
        }
        status = AnalysisStatus.COMPLETED;
        failureCode = null;
        updatedAt = now;
    }

    void fail(String failureCode, Instant now) {
        if (status != AnalysisStatus.ANALYZING && status != AnalysisStatus.RECOMMENDING) {
            throw new IllegalStateException("Analysis is not in a processable state");
        }
        status = AnalysisStatus.FAILED;
        this.failureCode = failureCode;
        updatedAt = now;
    }

    public UUID id() {
        return id;
    }

    public UUID userId() {
        return userId;
    }

    public UUID surveyId() {
        return surveyId;
    }

    public String surveySnapshot() {
        return surveySnapshot;
    }

    public Long budgetTotal() {
        return budgetTotal;
    }

    public AnalysisStatus status() {
        return status;
    }

    public String sourceResultId() {
        return sourceResultId;
    }

    public String failureCode() {
        return failureCode;
    }

    String cnnResultJson() {
        return cnnResultJson;
    }

    String llmResultJson() {
        return llmResultJson;
    }

    String surveyResultJson() {
        return surveyResultJson;
    }

    boolean hasRecommendationInputSnapshot() {
        return cnnResultJson != null && !cnnResultJson.isBlank();
    }

    byte[] photoData() {
        return photoData == null ? null : photoData.clone();
    }

    boolean hasPhoto() {
        return photoData != null || photoObjectKey != null;
    }

    String photoContentType() {
        return photoContentType;
    }

    PhotoStorageReference photoReference() {
        if (!hasPhoto()) {
            return null;
        }
        return PhotoStorageReference.restore(
                photoData, photoObjectKey, photoContentType, photoSize, photoChecksum);
    }

    void clearPhoto() {
        photoData = null;
        photoObjectKey = null;
        photoContentType = null;
        photoSize = null;
        photoChecksum = null;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
