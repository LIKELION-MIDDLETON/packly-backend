package com.jaungangton.api.survey;

public enum FaceArea implements SurveyOption {
    FOREHEAD(1), NOSE(2), CHEEKS(3), CHIN(4), EYE_AREA(5), WHOLE_FACE(6), NONE(7);

    private final int aiCode;

    FaceArea(int aiCode) {
        this.aiCode = aiCode;
    }

    @Override
    public int aiCode() {
        return aiCode;
    }
}
