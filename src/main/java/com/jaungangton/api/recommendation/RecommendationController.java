package com.jaungangton.api.recommendation;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.jaungangton.api.common.ApiException;

@RestController
@RequestMapping("/api/v1/recommendations")
public class RecommendationController {
    private final RecommendationQueryService service;

    public RecommendationController(RecommendationQueryService service) {
        this.service = service;
    }

    @GetMapping("/latest")
    RecommendationResultResponse latest(@AuthenticationPrincipal Jwt jwt) {
        return service.latest(currentUserId(jwt));
    }

    @GetMapping("/{recommendationId}")
    RecommendationResultResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID recommendationId) {
        return service.get(currentUserId(jwt), recommendationId);
    }

    @GetMapping
    RecommendationPageResponse list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit) {
        return service.list(currentUserId(jwt), cursor, limit);
    }

    private UUID currentUserId(Jwt jwt) {
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (RuntimeException exception) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_ACCESS_TOKEN", "Invalid access token subject.");
        }
    }
}
