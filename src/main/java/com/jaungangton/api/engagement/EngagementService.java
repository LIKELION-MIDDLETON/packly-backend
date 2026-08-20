package com.jaungangton.api.engagement;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.jaungangton.api.common.ApiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EngagementService {
    private final ProductUsageCompletionRepository usageRepository;
    private final RecommendationFeedbackRepository feedbackRepository;
    private final SosReportRepository sosRepository;
    private final EngagementOwnershipQuery ownership;
    private final Clock clock;

    @Autowired
    public EngagementService(ProductUsageCompletionRepository usageRepository,
                             RecommendationFeedbackRepository feedbackRepository,
                             SosReportRepository sosRepository,
                             EngagementOwnershipQuery ownership,
                             Clock clock) {
        this.usageRepository = usageRepository;
        this.feedbackRepository = feedbackRepository;
        this.sosRepository = sosRepository;
        this.ownership = ownership;
        this.clock = clock;
    }

    @Transactional
    public ProductUsageResponse putUsage(UUID userId, UUID recommendationId, UUID productId,
                                         ProductUsageRequest request) {
        requireOwnedProduct(userId, recommendationId, productId);
        Instant now = Instant.now(clock);
        ProductUsageCompletion completion = usageRepository
                .findByUserIdAndRecommendationProductIdAndUsedOn(userId, productId, request.usedOn())
                .map(existing -> {
                    existing.update(request.completed(), now);
                    return existing;
                })
                .orElseGet(() -> new ProductUsageCompletion(
                        UUID.randomUUID(), userId, productId, request.usedOn(), request.completed(), now));
        return ProductUsageResponse.from(recommendationId, usageRepository.save(completion));
    }

    @Transactional
    public FeedbackResponse putFeedback(UUID userId, UUID recommendationId, FeedbackRequest request) {
        requireOwnedRecommendation(userId, recommendationId);
        Instant now = Instant.now(clock);
        String comment = normalizeOptional(request.comment());
        RecommendationFeedback feedback = feedbackRepository.findByUserIdAndRecommendationId(userId, recommendationId)
                .map(existing -> {
                    existing.update(request.rating(), comment, now);
                    return existing;
                })
                .orElseGet(() -> new RecommendationFeedback(
                        UUID.randomUUID(), userId, recommendationId, request.rating(), comment, now));
        return FeedbackResponse.from(feedbackRepository.save(feedback));
    }

    @Transactional
    public SosReportResponse createSos(UUID userId, CreateSosReportRequest request) {
        if (request.recommendationId() != null) {
            requireOwnedRecommendation(userId, request.recommendationId());
        }
        Instant now = Instant.now(clock);
        SosReport report = new SosReport(
                UUID.randomUUID(), userId, request.recommendationId(), request.message().trim(),
                request.symptomLabels(), now);
        return SosReportResponse.from(sosRepository.save(report));
    }

    @Transactional(readOnly = true)
    public List<SosReportResponse> listSos(UUID userId) {
        return sosRepository.findAllByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(SosReportResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public SosReportResponse getSos(UUID userId, UUID reportId) {
        return sosRepository.findByIdAndUserId(reportId, userId)
                .map(SosReportResponse::from)
                .orElseThrow(this::notFound);
    }

    private void requireOwnedRecommendation(UUID userId, UUID recommendationId) {
        if (!ownership.ownsRecommendation(userId, recommendationId)) {
            throw notFound();
        }
    }

    private void requireOwnedProduct(UUID userId, UUID recommendationId, UUID productId) {
        if (!ownership.ownsProduct(userId, recommendationId, productId)) {
            throw notFound();
        }
    }

    private ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "요청한 리소스를 찾을 수 없습니다.");
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
