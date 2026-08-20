package com.jaungangton.api.analysis;

import jakarta.validation.constraints.PositiveOrZero;

public record CreateAnalysisRequest(@PositiveOrZero Long budgetTotal) {
}
