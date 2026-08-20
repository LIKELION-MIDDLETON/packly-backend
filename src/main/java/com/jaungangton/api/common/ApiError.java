package com.jaungangton.api.common;

import java.time.Instant;
import java.util.Map;

import org.springframework.http.HttpStatus;

public record ApiError(
        String type,
        String title,
        int status,
        String detail,
        String instance,
        String code,
        Map<String, String> fieldErrors,
        Instant timestamp
) {
    public static ApiError of(
            HttpStatus status,
            String code,
            String detail,
            String instance,
            Map<String, String> fieldErrors) {
        return new ApiError(
                "about:blank",
                status.getReasonPhrase(),
                status.value(),
                detail,
                instance,
                code,
                Map.copyOf(fieldErrors),
                Instant.now());
    }
}
