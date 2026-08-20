package com.jaungangton.api.recommendation;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jaungangton.api.common.ApiException;

@Service
public class RecommendationQueryService {
    private final RecommendationRepository repository;
    private final RecommendationMapper mapper;

    public RecommendationQueryService(RecommendationRepository repository, RecommendationMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public RecommendationResultResponse latest(UUID userId) {
        return repository.findFirstByUserIdOrderByCreatedAtDescIdDesc(userId)
                .map(mapper::toResponse)
                .orElseThrow(this::notFound);
    }

    @Transactional(readOnly = true)
    public RecommendationResultResponse get(UUID userId, UUID recommendationId) {
        return repository.findByIdAndUserId(recommendationId, userId)
                .map(mapper::toResponse)
                .orElseThrow(this::notFound);
    }

    @Transactional(readOnly = true)
    public RecommendationPageResponse list(UUID userId, String cursor, int limit) {
        if (limit < 1 || limit > 50) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_LIMIT", "limit must be between 1 and 50.");
        }
        Cursor position = decodeCursor(cursor);
        Slice<Recommendation> slice = position == null
                ? repository.findByUserIdOrderByCreatedAtDescIdDesc(userId, PageRequest.of(0, limit))
                : repository.findNext(userId, position.createdAt(), position.id(), PageRequest.of(0, limit));
        Recommendation last = slice.getContent().isEmpty() ? null
                : slice.getContent().get(slice.getContent().size() - 1);
        String next = slice.hasNext() && last != null ? encodeCursor(last.createdAt(), last.id()) : null;
        return new RecommendationPageResponse(slice.getContent().stream().map(mapper::toResponse).toList(), next);
    }

    @Transactional(readOnly = true)
    public Optional<RecommendationResultResponse> findByAnalysisId(UUID analysisId) {
        return repository.findByAnalysisId(analysisId).map(mapper::toResponse);
    }

    private Cursor decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            int separator = decoded.lastIndexOf('|');
            return new Cursor(Instant.parse(decoded.substring(0, separator)),
                    UUID.fromString(decoded.substring(separator + 1)));
        } catch (RuntimeException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CURSOR", "cursor is invalid.");
        }
    }

    private String encodeCursor(Instant createdAt, UUID id) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString((createdAt + "|" + id).getBytes(StandardCharsets.UTF_8));
    }

    private record Cursor(Instant createdAt, UUID id) {}

    private ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "RECOMMENDATION_NOT_FOUND",
                "추천 결과를 찾을 수 없습니다.");
    }
}
