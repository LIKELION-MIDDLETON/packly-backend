package com.jaungangton.api.engagement;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import com.jaungangton.api.common.ApiException;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EngagementController {
    private final EngagementService service;

    public EngagementController(EngagementService service) {
        this.service = service;
    }

    @PutMapping("/api/v1/recommendations/{recommendationId}/products/{productId}/usage")
    ProductUsageResponse putUsage(@AuthenticationPrincipal Jwt jwt,
                                  @PathVariable UUID recommendationId,
                                  @PathVariable UUID productId,
                                  @Valid @RequestBody ProductUsageRequest request) {
        return service.putUsage(currentUserId(jwt), recommendationId, productId, request);
    }

    @PutMapping("/api/v1/recommendations/{recommendationId}/feedback")
    FeedbackResponse putFeedback(@AuthenticationPrincipal Jwt jwt,
                                 @PathVariable UUID recommendationId,
                                 @Valid @RequestBody FeedbackRequest request) {
        return service.putFeedback(currentUserId(jwt), recommendationId, request);
    }

    @PostMapping("/api/v1/sos-reports")
    ResponseEntity<SosReportResponse> createSos(@AuthenticationPrincipal Jwt jwt,
                                                @Valid @RequestBody CreateSosReportRequest request) {
        SosReportResponse response = service.createSos(currentUserId(jwt), request);
        return ResponseEntity.created(URI.create("/api/v1/sos-reports/" + response.id())).body(response);
    }

    @GetMapping("/api/v1/sos-reports")
    List<SosReportResponse> listSos(@AuthenticationPrincipal Jwt jwt) {
        return service.listSos(currentUserId(jwt));
    }

    @GetMapping("/api/v1/sos-reports/{reportId}")
    SosReportResponse getSos(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID reportId) {
        return service.getSos(currentUserId(jwt), reportId);
    }

    private UUID currentUserId(Jwt jwt) {
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (RuntimeException exception) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_ACCESS_TOKEN", "Invalid access token subject.");
        }
    }
}
