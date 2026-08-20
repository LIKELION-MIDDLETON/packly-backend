package com.jaungangton.api.recommendation;

import java.util.List;

import org.springframework.stereotype.Component;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
class RecommendationMapper {
    private static final String PRODUCT_URL =
            "https://www.oliveyoung.co.kr/store/goods/getGoodsDetail.do?goodsNo=";

    private final ObjectMapper objectMapper;

    RecommendationMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    RecommendationResultResponse toResponse(Recommendation recommendation) {
        List<RecommendationProductResponse> products = recommendation.products().stream()
                .map(this::toResponse)
                .toList();
        return new RecommendationResultResponse(
                recommendation.id(), recommendation.analysisId(), recommendation.createdAt(),
                recommendation.diagnosis(), recommendation.headline(), recommendation.summary(),
                recommendation.confidence(), recommendation.triage(),
                new MedicalAdviceResponse(recommendation.medicalRecommended(),
                        stringList(recommendation.medicalReasons())),
                json(recommendation.reflectedSurvey()), products, recommendation.totalPrice(),
                recommendation.totalPriceDaily(), recommendation.analysisSummary(),
                stringList(recommendation.careRecommendations()),
                recommendation.disclaimer());
    }

    private RecommendationProductResponse toResponse(RecommendationProduct product) {
        return new RecommendationProductResponse(
                product.id(), product.orderIndex(), product.displayOrder(), product.applicationOrder(),
                product.usageGroup(), product.slot(), product.goodsNo(), product.brand(), product.name(),
                product.price(), product.dailyPrice(), product.dailyVolume(), product.totalVolume(),
                product.salePrice(), product.recommendationReason(), json(product.suitability()),
                product.suitabilitySource(), product.functionalInfo(), product.unscented(),
                product.comedogenicScore(), PRODUCT_URL + product.goodsNo());
    }

    private JsonNode json(String value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.readTree(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Persisted recommendation JSON is invalid", exception);
        }
    }

    private List<String> stringList(String value) {
        if (value == null) {
            return List.of();
        }
        try {
            return objectMapper.readValue(value, new TypeReference<List<String>>() { });
        } catch (JacksonException exception) {
            throw new IllegalStateException("Persisted recommendation list is invalid", exception);
        }
    }
}
