package com.jaungangton.api.engagement;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;

public record ProductUsageRequest(
        @NotNull LocalDate usedOn,
        @NotNull Boolean completed
) {
}
