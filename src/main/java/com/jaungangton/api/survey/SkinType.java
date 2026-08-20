package com.jaungangton.api.survey;

public enum SkinType implements SurveyOption {
    DRY(1), OILY(2), COMBINATION(3), DEHYDRATED_OILY(4), SENSITIVE(5), UNKNOWN(6);

    private final int aiCode;

    SkinType(int aiCode) {
        this.aiCode = aiCode;
    }

    @Override
    public int aiCode() {
        return aiCode;
    }
}
