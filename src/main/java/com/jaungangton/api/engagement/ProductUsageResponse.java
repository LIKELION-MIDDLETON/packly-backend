package com.jaungangton.api.engagement;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ProductUsageResponse(
        UUID id,
        UUID recommendationId,
        UUID productId,
        LocalDate usedOn,
        boolean completed,
        Instant createdAt,
        Instant updatedAt
) {
    static ProductUsageResponse from(UUID recommendationId, ProductUsageCompletion completion) {
        return new ProductUsageResponse(
                completion.id(), recommendationId, completion.recommendationProductId(),
                completion.usedOn(), completion.completed(), completion.createdAt(), completion.updatedAt());
    }
}
