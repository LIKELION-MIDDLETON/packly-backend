package com.jaungangton.api.survey;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

class SurveyMapperTest {
    private final SurveyMapper mapper = new SurveyMapper();

    @Test
    void mapsEveryUiEnumToTheAiContractCode() {
        assertThat(Arrays.stream(SkinType.values()).mapToInt(SkinType::aiCode)).containsExactly(1, 2, 3, 4, 5, 6);
        assertThat(Arrays.stream(SkinConcern.values()).mapToInt(SkinConcern::aiCode)).containsExactly(1, 2, 3, 4, 5, 6, 7, 8);
        assertThat(Arrays.stream(ConcernDuration.values()).mapToInt(ConcernDuration::aiCode)).containsExactly(1, 2, 3, 4, 5);
        assertThat(Arrays.stream(FaceArea.values()).mapToInt(FaceArea::aiCode)).containsExactly(1, 2, 3, 4, 5, 6, 7);
        assertThat(Arrays.stream(IrritationFrequency.values()).mapToInt(IrritationFrequency::aiCode)).containsExactly(1, 2, 3);
        assertThat(Arrays.stream(DiagnosedCondition.values()).mapToInt(DiagnosedCondition::aiCode)).containsExactly(1, 2, 3, 4, 5, 6);
    }

    @Test
    void createsAStableNumericSnapshotIncludingAreas() {
        SurveyRequest request = new SurveyRequest(
                SkinType.DEHYDRATED_OILY,
                List.of(SkinConcern.PIGMENTATION, SkinConcern.ACNE),
                ConcernDuration.OVER_THREE_MONTHS,
                List.of(FaceArea.CHIN, FaceArea.CHEEKS),
                IrritationFrequency.SOMETIMES,
                DiagnosedCondition.OTHER,
                "기타 진단");

        assertThat(mapper.toNumeric(request)).isEqualTo(
                new SurveyNumericSnapshot(4, List.of(1, 5), 5, List.of(3, 4), 2, 6));
    }
}
