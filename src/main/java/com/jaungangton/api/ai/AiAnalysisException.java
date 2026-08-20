package com.jaungangton.api.ai;

public class AiAnalysisException extends RuntimeException {
    private final String failureCode;

    public AiAnalysisException(String failureCode, String message) {
        super(message);
        this.failureCode = failureCode;
    }

    public AiAnalysisException(String failureCode, String message, Throwable cause) {
        super(message, cause);
        this.failureCode = failureCode;
    }

    public String failureCode() {
        return failureCode;
    }
}
