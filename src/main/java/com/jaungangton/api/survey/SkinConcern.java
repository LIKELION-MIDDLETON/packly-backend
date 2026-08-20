package com.jaungangton.api.survey;

public enum SkinConcern implements SurveyOption {
    ACNE(1), PORES_BLACKHEADS(2), REDNESS(3), DRYNESS_FLAKING(4), PIGMENTATION(5),
    WRINKLES_ELASTICITY(6), ITCHING_STINGING(7), NONE(8);

    private final int aiCode;

    SkinConcern(int aiCode) {
        this.aiCode = aiCode;
    }

    @Override
    public int aiCode() {
        return aiCode;
    }
}
