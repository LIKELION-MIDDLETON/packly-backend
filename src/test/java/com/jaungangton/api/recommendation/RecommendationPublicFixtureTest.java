package com.jaungangton.api.recommendation;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.Set;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class RecommendationPublicFixtureTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void zeroProductFixtureMatchesPublicResponseShapeAndExcludesUnconfirmedFields() throws Exception {
        try (InputStream input = getClass().getResourceAsStream(
                "/fixtures/public/recommendation-0-products.json")) {
            assertThat(input).isNotNull();
            JsonNode root = objectMapper.readTree(input);

            assertThat(fieldNames(root)).containsExactlyInAnyOrder(
                    "id", "analysisId", "createdAt", "diagnosis", "headline", "summary",
                    "confidence", "triage", "medicalAdvice", "reflectedSurvey", "products",
                    "totalPrice", "totalPriceDaily", "analysisSummary", "careRecommendations", "disclaimer");
            assertThat(root.path("products").isArray()).isTrue();
            assertThat(root.path("products")).isEmpty();
            assertThat(root.path("medicalAdvice").path("recommended").asBoolean()).isFalse();
            assertThat(root.path("medicalAdvice").path("reasons").isArray()).isTrue();
            assertThat(root.path("totalPriceDaily").asLong()).isZero();
            assertThat(root.toString()).doesNotContain(
                    "dailyPrice", "dailyVolume", "totalVolume", "salePrice", "recommendationReason",
                    "listPrice", "usage", "capacity");
        }
    }

    @Test
    void sevenProductFixtureExposesCanonicalDailyFieldsAndSevenSlots() throws Exception {
        try (InputStream input = getClass().getResourceAsStream(
                "/fixtures/public/recommendation-7-products.json")) {
            assertThat(input).isNotNull();
            JsonNode root = objectMapper.readTree(input);
            JsonNode first = root.path("products").get(0);
            JsonNode protect = root.path("products").get(5);

            assertThat(root.path("products")).hasSize(7);
            assertThat(first.path("dailyPrice").asLong()).isEqualTo(1000L);
            assertThat(first.path("salePrice").asLong()).isEqualTo(10000L);
            assertThat(first.path("applicationOrder").asInt()).isEqualTo(1);
            assertThat(protect.path("dailyPrice").isNull()).isTrue();
            assertThat(protect.path("applicationOrder").isNull()).isTrue();
            assertThat(protect.path("usageGroup").asText()).isEqualTo("PROTECT");
            assertThat(root.path("totalPriceDaily").asLong()).isEqualTo(4286L);
            assertThat(root.toString()).doesNotContain(
                    "dailyVolumePerDay", "totalVolumeMl", "listPrice", "capacity");
        }
    }

    private Set<String> fieldNames(JsonNode node) {
        Set<String> names = new java.util.HashSet<>();
        node.properties().forEach(entry -> names.add(entry.getKey()));
        return names;
    }
}
