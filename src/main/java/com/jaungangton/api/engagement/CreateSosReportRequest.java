package com.jaungangton.api.engagement;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateSosReportRequest(
        UUID recommendationId,
        @NotBlank @Size(max = 2000) String message,
        @Size(max = 20) List<@NotBlank @Size(max = 64)
                @Pattern(regexp = "[A-Za-z][A-Za-z0-9_]*") String> symptomLabels
) {
}
