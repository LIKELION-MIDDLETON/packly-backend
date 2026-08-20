package com.jaungangton.api.survey;

public enum DiagnosedCondition implements SurveyOption {
    NONE(1), ATOPIC_DERMATITIS(2), SEVERE_ACNE(3), SEBORRHEIC_DERMATITIS(4), PSORIASIS(5), OTHER(6);

    private final int aiCode;

    DiagnosedCondition(int aiCode) {
        this.aiCode = aiCode;
    }

    @Override
    public int aiCode() {
        return aiCode;
    }
}
