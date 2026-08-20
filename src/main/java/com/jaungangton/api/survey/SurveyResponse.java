package com.jaungangton.api.survey;

import java.time.Instant;
import java.util.List;

public record SurveyResponse(
        SkinType skinType,
        List<SkinConcern> concerns,
        ConcernDuration duration,
        List<FaceArea> areas,
        IrritationFrequency irritation,
        DiagnosedCondition diagnosed,
        String otherDiagnosis,
        Instant submittedAt,
        SurveyNumericSnapshot aiNumericSnapshot) {
}
