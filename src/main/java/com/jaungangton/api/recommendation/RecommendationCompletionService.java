package com.jaungangton.api.recommendation;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jaungangton.api.ai.AiRecommendationExchange;
import com.jaungangton.api.ai.AiRecommendationProduct;
import com.jaungangton.api.ai.AiRecommendationResult;
import com.jaungangton.api.analysis.AnalysisResultAcceptanceService;
import com.jaungangton.api.analysis.RecommendationWork;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class RecommendationCompletionService {
    private final RecommendationRepository repository;
    private final RecommendationMapper mapper;
    private final AnalysisResultAcceptanceService analysisService;
    private final ProductImageProvider productImageProvider;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public RecommendationCompletionService(
            RecommendationRepository repository,
            RecommendationMapper mapper,
            AnalysisResultAcceptanceService analysisService,
            ProductImageProvider productImageProvider,
            ObjectMapper objectMapper,
            Clock clock) {
        this.repository = repository;
        this.mapper = mapper;
        this.analysisService = analysisService;
        this.productImageProvider = productImageProvider;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public RecommendationResultResponse complete(RecommendationWork work, AiRecommendationExchange exchange) {
        Recommendation existing = repository.findByAnalysisId(work.analysisId()).orElse(null);
        if (existing != null) {
            return mapper.toResponse(existing);
        }
        AiRecommendationResult result = exchange.result();
        Recommendation recommendation = new Recommendation(
                UUID.randomUUID(), work.analysisId(), work.userId(), result.diagnosis(), result.headline(),
                result.summary(), result.confidence(), result.triage(), result.medicalRecommended(),
                json(result.medicalReasons()), nullableJson(result.reflectedSurvey()), result.totalPrice(),
                result.totalPriceDaily(), result.analysisSummary(), json(result.careRecommendations()), result.disclaimer(),
                exchange.requestSnapshot(), exchange.responseSnapshot(), Instant.now(clock));
        for (AiRecommendationProduct product : result.products()) {
            recommendation.addProduct(new RecommendationProduct(
                    UUID.randomUUID(), recommendation, product.displayOrder(), product.applicationOrder(),
                    product.usageGroup(), product.slot(), product.goodsNo(),
                    product.brand(), product.name(), product.price(), nullableJson(product.suitability()),
                    product.suitabilitySource(), product.functionalInfo(), product.unscented(),
                    product.comedogenicScore(), product.dailyPrice(), product.dailyVolume(),
                    product.totalVolume(), product.salePrice(), product.recommendationReason(),
                    productImageUrl(product.brand(), product.name())));
        }
        Recommendation saved = repository.save(recommendation);
        analysisService.markCompleted(work.analysisId());
        return mapper.toResponse(saved);
    }

    private String productImageUrl(String brand, String name) {
        try {
            return productImageProvider.findImageUrl(brand, name).orElse(null);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private String nullableJson(JsonNode value) {
        return value == null || value.isNull() ? null : json(value);
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Could not persist AI recommendation JSON", exception);
        }
    }
}
