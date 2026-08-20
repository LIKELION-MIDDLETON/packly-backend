package com.jaungangton.api.survey;

public enum ConcernDuration implements SurveyOption {
    NOT_APPLICABLE(1), UP_TO_ONE_WEEK(2), ONE_TO_FOUR_WEEKS(3), ONE_TO_THREE_MONTHS(4), OVER_THREE_MONTHS(5);

    private final int aiCode;

    ConcernDuration(int aiCode) {
        this.aiCode = aiCode;
    }

    @Override
    public int aiCode() {
        return aiCode;
    }
}
