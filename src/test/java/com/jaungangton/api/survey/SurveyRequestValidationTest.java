package com.jaungangton.api.survey;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SurveyRequestValidationTest {
    private static jakarta.validation.ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @Test
    void noneSelectionsAreExclusive() {
        SurveyRequest request = validRequest(
                List.of(SkinConcern.NONE, SkinConcern.ACNE),
                List.of(FaceArea.NONE, FaceArea.CHEEKS),
                DiagnosedCondition.NONE,
                null);

        assertThat(validator.validate(request)).extracting(violation -> violation.getPropertyPath().toString())
                .contains("concernSelectionValid", "areaSelectionValid");
    }

    @Test
    void duplicateMultiSelectionsAreRejected() {
        SurveyRequest request = validRequest(
                List.of(SkinConcern.ACNE, SkinConcern.ACNE),
                List.of(FaceArea.CHEEKS, FaceArea.CHEEKS),
                DiagnosedCondition.NONE,
                null);

        assertThat(validator.validate(request)).extracting(violation -> violation.getPropertyPath().toString())
                .contains("concernSelectionValid", "areaSelectionValid");
    }

    @Test
    void otherDiagnosisRequiresTextAndOtherTextIsRejectedForKnownDiagnosis() {
        SurveyRequest missing = validRequest(
                List.of(SkinConcern.ACNE), List.of(FaceArea.CHEEKS), DiagnosedCondition.OTHER, " ");
        SurveyRequest unexpected = validRequest(
                List.of(SkinConcern.ACNE), List.of(FaceArea.CHEEKS), DiagnosedCondition.NONE, "text");

        assertThat(validator.validate(missing)).anyMatch(v -> v.getPropertyPath().toString().equals("otherDiagnosisValid"));
        assertThat(validator.validate(unexpected)).anyMatch(v -> v.getPropertyPath().toString().equals("otherDiagnosisValid"));
    }

    private SurveyRequest validRequest(List<SkinConcern> concerns, List<FaceArea> areas,
            DiagnosedCondition diagnosed, String otherDiagnosis) {
        return new SurveyRequest(
                SkinType.DRY,
                concerns,
                ConcernDuration.UP_TO_ONE_WEEK,
                areas,
                IrritationFrequency.NEVER,
                diagnosed,
                otherDiagnosis);
    }
}
