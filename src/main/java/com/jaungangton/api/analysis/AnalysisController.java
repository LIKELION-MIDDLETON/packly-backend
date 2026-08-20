package com.jaungangton.api.analysis;

import java.net.URI;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

import com.jaungangton.api.common.ApiException;

@RestController
@RequestMapping("/api/v1/analyses")
public class AnalysisController {
    private final AnalysisService service;
    private final PhotoAnalysisWorkflowService photoWorkflow;

    public AnalysisController(AnalysisService service, PhotoAnalysisWorkflowService photoWorkflow) {
        this.service = service;
        this.photoWorkflow = photoWorkflow;
    }

    @PostMapping(path = "/{analysisId}/photo", consumes = "multipart/form-data")
    ResponseEntity<AnalysisResponse> uploadPhoto(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID analysisId,
            @RequestPart("image") MultipartFile[] images) {
        if (images == null || images.length != 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ONE_PHOTO_REQUIRED",
                    "image 파일은 하나만 업로드할 수 있습니다.");
        }
        AnalysisResponse response = photoWorkflow.uploadAndStart(currentUserId(jwt), analysisId, images[0]);
        return ResponseEntity.accepted()
                .location(URI.create("/api/v1/analyses/" + response.id()))
                .body(response);
    }

    @PostMapping
    ResponseEntity<AnalysisResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody(required = false) CreateAnalysisRequest request) {
        CreateAnalysisRequest body = request == null ? new CreateAnalysisRequest(null) : request;
        AnalysisResponse response = service.create(currentUserId(jwt), idempotencyKey, body);
        return ResponseEntity.accepted()
                .location(URI.create("/api/v1/analyses/" + response.id()))
                .body(response);
    }

    @GetMapping("/{analysisId}")
    AnalysisResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID analysisId) {
        return service.get(currentUserId(jwt), analysisId);
    }

    private UUID currentUserId(Jwt jwt) {
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (RuntimeException exception) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_ACCESS_TOKEN", "Invalid access token subject.");
        }
    }
}
