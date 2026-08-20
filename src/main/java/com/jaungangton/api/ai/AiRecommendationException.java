package com.jaungangton.api.ai;

public class AiRecommendationException extends RuntimeException {
    private final String failureCode;

    public AiRecommendationException(String failureCode, String message) {
        super(message);
        this.failureCode = failureCode;
    }

    public AiRecommendationException(String failureCode, String message, Throwable cause) {
        super(message, cause);
        this.failureCode = failureCode;
    }

    public String failureCode() {
        return failureCode;
    }
}
