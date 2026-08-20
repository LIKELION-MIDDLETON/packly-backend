package com.jaungangton.api.ai;

public interface AiRecommendationPort {
    AiRecommendationExchange recommend(AiRecommendationRequest request);
}
