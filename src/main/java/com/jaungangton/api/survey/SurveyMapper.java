package com.jaungangton.api.survey;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

@Service
public class SurveyMapper {

    public SurveyNumericSnapshot toNumeric(SurveyRequest request) {
        return new SurveyNumericSnapshot(
                request.skinType().aiCode(),
                codes(request.concerns()),
                request.duration().aiCode(),
                codes(request.areas()),
                request.irritation().aiCode(),
                request.diagnosed().aiCode());
    }

    SurveyResponse toResponse(Survey survey) {
        return new SurveyResponse(
                fromCode(SkinType.class, survey.skinType()),
                fromCsv(SkinConcern.class, survey.concerns()),
                fromCode(ConcernDuration.class, survey.duration()),
                fromCsv(FaceArea.class, survey.areas()),
                fromCode(IrritationFrequency.class, survey.irritation()),
                fromCode(DiagnosedCondition.class, survey.diagnosed()),
                survey.otherDiagnosis(),
                survey.submittedAt(),
                new SurveyNumericSnapshot(
                        survey.skinType(),
                        parseCsv(survey.concerns()),
                        survey.duration(),
                        parseCsv(survey.areas()),
                        survey.irritation(),
                        survey.diagnosed()));
    }

    String toCsv(List<? extends SurveyOption> values) {
        return codes(values).stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    private List<Integer> codes(List<? extends SurveyOption> values) {
        return values.stream()
                .map(SurveyOption::aiCode)
                .sorted()
                .toList();
    }

    private List<Integer> parseCsv(String value) {
        return Arrays.stream(value.split(","))
                .map(Integer::parseInt)
                .toList();
    }

    private <E extends Enum<E> & SurveyOption> List<E> fromCsv(Class<E> type, String value) {
        return parseCsv(value).stream().map(code -> fromCode(type, code)).toList();
    }

    private <E extends Enum<E> & SurveyOption> E fromCode(Class<E> type, int code) {
        return Arrays.stream(type.getEnumConstants())
                .filter(value -> value.aiCode() == code)
                .min(Comparator.comparingInt(SurveyOption::aiCode))
                .orElseThrow(() -> new IllegalStateException("Unknown persisted survey code"));
    }
}
