package com.jaungangton.api.survey;

import java.util.List;

public record SurveyNumericSnapshot(
        int skinType,
        List<Integer> concerns,
        int duration,
        List<Integer> areas,
        int irritation,
        int diagnosed) {
}
