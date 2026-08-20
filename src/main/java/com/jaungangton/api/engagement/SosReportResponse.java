package com.jaungangton.api.engagement;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SosReportResponse(
        UUID id,
        UUID recommendationId,
        String message,
        List<String> symptomLabels,
        SosStatus status,
        Instant createdAt,
        Instant updatedAt
) {
    static SosReportResponse from(SosReport report) {
        return new SosReportResponse(
                report.id(), report.recommendationId(), report.message(), report.symptomLabels(), report.status(),
                report.createdAt(), report.updatedAt());
    }
}
