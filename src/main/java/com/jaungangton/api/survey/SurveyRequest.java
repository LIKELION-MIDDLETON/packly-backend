package com.jaungangton.api.survey;

import java.util.List;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SurveyRequest(
        @NotNull SkinType skinType,
        @NotEmpty @Size(max = 8) List<@NotNull SkinConcern> concerns,
        @NotNull ConcernDuration duration,
        @NotEmpty @Size(max = 7) List<@NotNull FaceArea> areas,
        @NotNull IrritationFrequency irritation,
        @NotNull DiagnosedCondition diagnosed,
        @Size(max = 500) String otherDiagnosis) {

    @AssertTrue(message = "concerns must be unique and NONE cannot be combined with other concerns")
    public boolean isConcernSelectionValid() {
        return concerns == null || (concerns.stream().distinct().count() == concerns.size()
                && (!concerns.contains(SkinConcern.NONE) || concerns.size() == 1));
    }

    @AssertTrue(message = "areas must be unique and NONE cannot be combined with other areas")
    public boolean isAreaSelectionValid() {
        return areas == null || (areas.stream().distinct().count() == areas.size()
                && (!areas.contains(FaceArea.NONE) || areas.size() == 1));
    }

    @AssertTrue(message = "otherDiagnosis is required only when diagnosed is OTHER")
    public boolean isOtherDiagnosisValid() {
        if (diagnosed == null) {
            return true;
        }
        boolean hasText = otherDiagnosis != null && !otherDiagnosis.isBlank();
        return diagnosed == DiagnosedCondition.OTHER ? hasText : !hasText;
    }
}
