package com.jaungangton.api.survey;

public enum IrritationFrequency implements SurveyOption {
    NEVER(1), SOMETIMES(2), OFTEN(3);

    private final int aiCode;

    IrritationFrequency(int aiCode) {
        this.aiCode = aiCode;
    }

    @Override
    public int aiCode() {
        return aiCode;
    }
}
