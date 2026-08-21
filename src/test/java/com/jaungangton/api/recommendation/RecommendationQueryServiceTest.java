package com.jaungangton.api.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.SliceImpl;

import com.jaungangton.api.common.ApiException;

import tools.jackson.databind.ObjectMapper;

class RecommendationQueryServiceTest {
    @Test
    void getUsesOwnerScopedRepositoryLookup() {
        RecommendationRepository repository = mock(RecommendationRepository.class);
        RecommendationMapper mapper = mock(RecommendationMapper.class);
        UUID userId = UUID.randomUUID();
        UUID recommendationId = UUID.randomUUID();
        Recommendation entity = entity(userId, recommendationId);
        RecommendationResultResponse expected = response(entity);
        when(repository.findByIdAndUserId(recommendationId, userId)).thenReturn(Optional.of(entity));
        when(mapper.toResponse(entity)).thenReturn(expected);
        RecommendationQueryService service = new RecommendationQueryService(repository, mapper);

        assertThat(service.get(userId, recommendationId)).isEqualTo(expected);
        verify(repository).findByIdAndUserId(recommendationId, userId);
    }

    @Test
    void invalidCursorIsRejectedAndPaginationReturnsOpaqueNextCursor() {
        RecommendationRepository repository = mock(RecommendationRepository.class);
        RecommendationMapper mapper = mock(RecommendationMapper.class);
        UUID userId = UUID.randomUUID();
        Recommendation entity = entity(userId, UUID.randomUUID());
        when(repository.findByUserIdOrderByCreatedAtDescIdDesc(any(), any()))
                .thenReturn(new SliceImpl<>(List.of(entity), PageRequest.of(0, 1), true));
        when(mapper.toResponse(entity)).thenReturn(response(entity));
        RecommendationQueryService service = new RecommendationQueryService(repository, mapper);

        RecommendationPageResponse page = service.list(userId, null, 1);

        assertThat(page.items()).hasSize(1);
        assertThat(page.nextCursor()).isNotBlank()
                .isNotEqualTo(entity.createdAt() + "|" + entity.id());
        assertThatThrownBy(() -> service.list(userId, "not-base64!", 1))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.code()).isEqualTo("INVALID_CURSOR"));
    }

    @Test
    void mapperComputesProductUrlAndKeepsCamelCaseDomainFields() {
        UUID userId = UUID.randomUUID();
        Recommendation recommendation = entity(userId, UUID.randomUUID());
        recommendation.addProduct(new RecommendationProduct(
                UUID.randomUUID(), recommendation, 1, "토너", "A0001", "Brand", "Name", 12000,
                "{\"lowIrritation\":68.0}", "MEASURED", null, true, 0));

        RecommendationResultResponse response = new RecommendationMapper(new ObjectMapper()).toResponse(recommendation);

        assertThat(response.products()).singleElement().satisfies(product -> {
            assertThat(product.goodsNo()).isEqualTo("A0001");
            assertThat(product.productUrl()).endsWith("goodsNo=A0001");
            assertThat(product.suitability().get("lowIrritation").asDouble()).isEqualTo(68.0);
        });
        assertThat(response.purchaseOptions()).isEmpty();
    }

    @Test
    void mapperBuildsTwoAndFourWeekPurchaseOptionsFromTheAiDailyTotal() {
        UUID userId = UUID.randomUUID();
        Recommendation recommendation = new Recommendation(
                UUID.randomUUID(), UUID.randomUUID(), userId, "normal", "headline", "summary", 0.9,
                "normal", false, "[]", null, 0L, 4286L, null, "[]", null, "{}", "{}",
                Instant.parse("2026-08-19T00:00:00Z"));

        RecommendationResultResponse response = new RecommendationMapper(new ObjectMapper()).toResponse(recommendation);

        assertThat(response.purchaseOptions()).containsExactly(
                new RecommendationPurchaseOptionResponse(14, "2주", 60004L),
                new RecommendationPurchaseOptionResponse(28, "4주", 120008L));
    }

    private Recommendation entity(UUID userId, UUID id) {
        return new Recommendation(
                id, UUID.randomUUID(), userId, "normal", "headline", "summary", 0.9, "normal", false,
                "[]", null, 0, null, "[]", null, "{}", "{}", Instant.parse("2026-08-19T00:00:00Z"));
    }

    private RecommendationResultResponse response(Recommendation entity) {
        return new RecommendationMapper(new ObjectMapper()).toResponse(entity);
    }
}
