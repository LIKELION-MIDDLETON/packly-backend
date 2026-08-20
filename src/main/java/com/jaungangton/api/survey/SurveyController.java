package com.jaungangton.api.survey;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jaungangton.api.common.ApiException;

@RestController
@RequestMapping("/api/v1/me/survey")
public class SurveyController {
    private final SurveyService service;

    public SurveyController(SurveyService service) {
        this.service = service;
    }

    @GetMapping
    SurveyResponse get(@AuthenticationPrincipal Jwt jwt) {
        return service.get(currentUserId(jwt));
    }

    @PutMapping
    SurveyResponse put(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody SurveyRequest request) {
        return service.upsert(currentUserId(jwt), request);
    }

    private UUID currentUserId(Jwt jwt) {
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (RuntimeException exception) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_ACCESS_TOKEN", "Invalid access token subject.");
        }
    }
}
