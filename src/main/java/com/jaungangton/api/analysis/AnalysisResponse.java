package com.jaungangton.api.analysis;

import java.time.Instant;
import java.util.UUID;

public record AnalysisResponse(
        UUID id,
        AnalysisStatus status,
        Long budgetTotal,
        String failureCode,
        Instant createdAt,
        Instant updatedAt,
        UUID recommendationId) {

    static AnalysisResponse from(AnalysisJob job) {
        return from(job, null);
    }

    static AnalysisResponse from(AnalysisJob job, UUID recommendationId) {
        return new AnalysisResponse(
                job.id(), job.status(), job.budgetTotal(), job.failureCode(), job.createdAt(), job.updatedAt(),
                recommendationId);
    }
}
