package com.jaungangton.api.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.ObjectMapper;

class FastApiRecommendationAdapterTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void sendsSnakeCaseRequestAndNormalizesKoreanResponseWithOneProduct() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://ai.example");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        FastApiRecommendationAdapter adapter = new FastApiRecommendationAdapter(builder.build(), objectMapper);
        server.expect(requestTo("http://ai.example/recommend"))
                .andExpect(content().json("""
                        {"cnn_result":{"predicted_label":"normal","confidence":0.91},
                         "llm_result":null,
                         "survey":{"skin_type":1,"concerns":[4]},
                         "budget_total":50000,"alpha":0.6}
                        """))
                .andRespond(withSuccess(response(1).replace(
                        "\"반영된설문\":null",
                        "\"반영된설문\":{\"피부타입\":\"건성\",\"고민\":[\"건조\"],\"무향필터\":\"강제\"}"),
                        MediaType.APPLICATION_JSON));

        AiRecommendationExchange exchange = adapter.recommend(request());

        assertThat(exchange.result().diagnosis()).isEqualTo("normal");
        assertThat(exchange.result().triage()).isEqualTo("NORMAL");
        assertThat(exchange.result().medicalReasons()).containsExactly("AI 분석 소견");
        assertThat(exchange.result().products()).hasSize(1);
        assertThat(exchange.result().products().get(0).goodsNo()).isEqualTo("A1");
        assertThat(exchange.result().products().get(0).suitabilitySource()).isEqualTo("MEASURED");
        assertThat(exchange.result().products().get(0).suitability().get("lowIrritation").asDouble())
                .isEqualTo(68.0);
        assertThat(exchange.result().analysisSummary()).isNull();
        assertThat(exchange.result().reflectedSurvey().get("skinType").asText()).isEqualTo("건성");
        assertThat(exchange.result().reflectedSurvey().has("피부타입")).isFalse();
        server.verify();
    }

    @Test
    void acceptsZeroOrThreeProductsAndMissingOptionalLlmFields() {
        for (int count : new int[] {0, 3}) {
            RestClient.Builder builder = RestClient.builder().baseUrl("http://ai.example");
            MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
            FastApiRecommendationAdapter adapter = new FastApiRecommendationAdapter(builder.build(), objectMapper);
            server.expect(requestTo("http://ai.example/recommend"))
                    .andRespond(withSuccess(response(count), MediaType.APPLICATION_JSON));

            AiRecommendationResult result = adapter.recommend(request()).result();

            assertThat(result.products()).hasSize(count);
            assertThat(result.careRecommendations()).isEmpty();
            assertThat(result.disclaimer()).isNull();
            server.verify();
        }
    }

    @Test
    void acceptsAllSevenDisplaySlotsAndSeparatesApplicationOrderFromDisplayOrder() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://ai.example");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        FastApiRecommendationAdapter adapter = new FastApiRecommendationAdapter(builder.build(), objectMapper);
        server.expect(requestTo("http://ai.example/recommend"))
                .andRespond(withSuccess(response(7), MediaType.APPLICATION_JSON));

        List<AiRecommendationProduct> products = adapter.recommend(request()).result().products();

        assertThat(products).hasSize(7);
        assertThat(products).extracting(AiRecommendationProduct::displayOrder)
                .containsExactly(1, 2, 3, 4, 5, 6, 7);
        assertThat(products).extracting(AiRecommendationProduct::applicationOrder)
                .containsExactly(1, 2, 3, null, null, null, null);
        assertThat(products).extracting(AiRecommendationProduct::usageGroup)
                .containsExactly("CORE_ROUTINE", "CORE_ROUTINE", "CORE_ROUTINE", "CLEANSE",
                        "TREATMENT", "PROTECT", "OCCASIONAL");
        server.verify();
    }

    @Test
    void readsTheCanonicalPostPr9FixtureAndKeepsNullableDailyFieldsNullable() throws Exception {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://ai.example");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        FastApiRecommendationAdapter adapter = new FastApiRecommendationAdapter(builder.build(), objectMapper);
        String fixture;
        try (InputStream input = getClass().getResourceAsStream(
                "/fixtures/ai-v5/recommend-response-pr9.json")) {
            assertThat(input).isNotNull();
            fixture = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        server.expect(requestTo("http://ai.example/recommend"))
                .andRespond(withSuccess(fixture, MediaType.APPLICATION_JSON));

        AiRecommendationResult result = adapter.recommend(request()).result();

        assertThat(result.products()).hasSize(7);
        assertThat(result.totalPriceDaily()).isEqualTo(4286L);
        assertThat(result.totalPrice()).isEqualTo(70000L);
        assertThat(result.products().get(0).dailyPrice()).isEqualTo(1000L);
        assertThat(result.products().get(0).salePrice()).isEqualTo(10000L);
        assertThat(result.products().get(0).recommendationReason()).isNull();
        assertThat(result.products().get(5).dailyPrice()).isNull();
        assertThat(result.products().get(5).totalVolume()).isEqualTo("50g");
        server.verify();
    }

    @Test
    void acceptsTheLegacyPriceAndTotalOnlyAsFallbackWithoutUsingDailyPrice() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://ai.example");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        FastApiRecommendationAdapter adapter = new FastApiRecommendationAdapter(builder.build(), objectMapper);
        String legacy = response(1)
                .replace(",\"총액_일일\":1000", ",\"총액\":10000")
                .replace("\"일일가격\":1000,\"일일용량\":\"2ml\",\"전체용량\":\"200ml\",\"판매가\":10000,\"추천이유\":null,",
                        "\"가격\":10000,");
        server.expect(requestTo("http://ai.example/recommend"))
                .andRespond(withSuccess(legacy, MediaType.APPLICATION_JSON));

        AiRecommendationResult result = adapter.recommend(request()).result();

        assertThat(result.totalPrice()).isEqualTo(10000L);
        assertThat(result.totalPriceDaily()).isNull();
        assertThat(result.products().get(0).price()).isEqualTo(10000L);
        assertThat(result.products().get(0).dailyPrice()).isNull();
        assertThat(result.products().get(0).salePrice()).isNull();
        server.verify();
    }

    @Test
    void doesNotMapDailyPriceIntoLegacyPriceWhenSalePriceIsNull() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://ai.example");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        FastApiRecommendationAdapter adapter = new FastApiRecommendationAdapter(builder.build(), objectMapper);
        String responseWithUnknownSalePrice = response(1).replace("\"판매가\":10000", "\"판매가\":null");
        server.expect(requestTo("http://ai.example/recommend"))
                .andRespond(withSuccess(responseWithUnknownSalePrice, MediaType.APPLICATION_JSON));

        AiRecommendationProduct product = adapter.recommend(request()).result().products().get(0);

        assertThat(product.dailyPrice()).isEqualTo(1000L);
        assertThat(product.salePrice()).isNull();
        assertThat(product.price()).isNull();
        server.verify();
    }

    @Test
    void usesOriginalThirdSlotPositionEvenWhenTheDiagnosisChangesItsSlotName() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://ai.example");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        FastApiRecommendationAdapter adapter = new FastApiRecommendationAdapter(builder.build(), objectMapper);
        server.expect(requestTo("http://ai.example/recommend"))
                .andRespond(withSuccess(response(3).replace("\"슬롯\":\"크림\"", "\"슬롯\":\"논코메도닉 수분젤\""), MediaType.APPLICATION_JSON));

        AiRecommendationProduct product = adapter.recommend(request()).result().products().get(2);

        assertThat(product.slot()).isEqualTo("논코메도닉 수분젤");
        assertThat(product.displayOrder()).isEqualTo(3);
        assertThat(product.applicationOrder()).isEqualTo(3);
        assertThat(product.usageGroup()).isEqualTo("CORE_ROUTINE");
        server.verify();
    }

    @Test
    void treatsDisplayOrderAsAuthoritativeAndRejectsConflictingDeprecatedOrderAlias() {
        RestClient.Builder preferredBuilder = RestClient.builder().baseUrl("http://ai.example");
        MockRestServiceServer preferredServer = MockRestServiceServer.bindTo(preferredBuilder).build();
        FastApiRecommendationAdapter preferredAdapter = new FastApiRecommendationAdapter(preferredBuilder.build(), objectMapper);
        preferredServer.expect(requestTo("http://ai.example/recommend"))
                .andRespond(withSuccess(response(1).replace("\"순서\":1", "\"순서\":3,\"displayOrder\":3"),
                        MediaType.APPLICATION_JSON));

        assertThat(preferredAdapter.recommend(request()).result().products().get(0).displayOrder()).isEqualTo(3);
        preferredServer.verify();

        RestClient.Builder conflictBuilder = RestClient.builder().baseUrl("http://ai.example");
        MockRestServiceServer conflictServer = MockRestServiceServer.bindTo(conflictBuilder).build();
        FastApiRecommendationAdapter conflictAdapter = new FastApiRecommendationAdapter(conflictBuilder.build(), objectMapper);
        conflictServer.expect(requestTo("http://ai.example/recommend"))
                .andRespond(withSuccess(response(1).replace("\"순서\":1", "\"순서\":1,\"displayOrder\":2"),
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> conflictAdapter.recommend(request()))
                .isInstanceOfSatisfying(AiRecommendationException.class,
                        exception -> assertThat(exception.failureCode()).isEqualTo("AI_INVALID_RESPONSE"));
        conflictServer.verify();
    }

    @Test
    void rejectsDuplicateButAllowsMissingDisplayOrdersFromFilteredSlots() {
        RestClient.Builder duplicateBuilder = RestClient.builder().baseUrl("http://ai.example");
        MockRestServiceServer duplicateServer = MockRestServiceServer.bindTo(duplicateBuilder).build();
        FastApiRecommendationAdapter duplicateAdapter = new FastApiRecommendationAdapter(duplicateBuilder.build(), objectMapper);
        duplicateServer.expect(requestTo("http://ai.example/recommend"))
                .andRespond(withSuccess(response(2).replace("\"순서\":2", "\"순서\":1"), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> duplicateAdapter.recommend(request()))
                .isInstanceOfSatisfying(AiRecommendationException.class,
                        exception -> assertThat(exception.failureCode()).isEqualTo("AI_INVALID_RESPONSE"));
        duplicateServer.verify();

        RestClient.Builder missingBuilder = RestClient.builder().baseUrl("http://ai.example");
        MockRestServiceServer missingServer = MockRestServiceServer.bindTo(missingBuilder).build();
        FastApiRecommendationAdapter missingAdapter = new FastApiRecommendationAdapter(missingBuilder.build(), objectMapper);
        missingServer.expect(requestTo("http://ai.example/recommend"))
                .andRespond(withSuccess(response(2).replace("\"순서\":2", "\"순서\":3"), MediaType.APPLICATION_JSON));

        assertThat(missingAdapter.recommend(request()).result().products())
                .extracting(AiRecommendationProduct::displayOrder)
                .containsExactly(1, 3);
        missingServer.verify();
    }

    @Test
    void acceptsAnEighthCompatibilitySlotAsOccasional() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://ai.example");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        FastApiRecommendationAdapter adapter = new FastApiRecommendationAdapter(builder.build(), objectMapper);
        server.expect(requestTo("http://ai.example/recommend"))
                .andRespond(withSuccess(response(1).replace("\"순서\":1", "\"순서\":8"),
                        MediaType.APPLICATION_JSON));

        assertThat(adapter.recommend(request()).result().products().get(0))
                .satisfies(product -> {
                    assertThat(product.displayOrder()).isEqualTo(8);
                    assertThat(product.applicationOrder()).isNull();
                    assertThat(product.usageGroup()).isEqualTo("OCCASIONAL");
                });
        server.verify();
    }

    @Test
    void distinguishesBadRequestServerErrorAndInvalidResponse() {
        assertFailure(withBadRequest(), "AI_INVALID_REQUEST");
        assertFailure(withServerError(), "AI_SERVER_ERROR");

        RestClient.Builder builder = RestClient.builder().baseUrl("http://ai.example");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        FastApiRecommendationAdapter adapter = new FastApiRecommendationAdapter(builder.build(), objectMapper);
        server.expect(requestTo("http://ai.example/recommend"))
                .andRespond(withSuccess("{\"unexpected\":true}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> adapter.recommend(request()))
                .isInstanceOfSatisfying(AiRecommendationException.class,
                        exception -> assertThat(exception.failureCode()).isEqualTo("AI_INVALID_RESPONSE"));
    }

    @Test
    void rejectsMissingRequiredBooleanInsteadOfTreatingItAsFalse() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://ai.example");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        FastApiRecommendationAdapter adapter = new FastApiRecommendationAdapter(builder.build(), objectMapper);
        String missingBoolean = response(0).replace("\"의료상담권고\":true,", "");
        server.expect(requestTo("http://ai.example/recommend"))
                .andRespond(withSuccess(missingBoolean, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> adapter.recommend(request()))
                .isInstanceOfSatisfying(AiRecommendationException.class,
                        exception -> assertThat(exception.failureCode()).isEqualTo("AI_INVALID_RESPONSE"));
    }

    @Test
    void requiresAnExplicitProductCollectionButAllowsItToBeEmpty() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://ai.example");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        FastApiRecommendationAdapter adapter = new FastApiRecommendationAdapter(builder.build(), objectMapper);
        String missingProducts = response(0).replace(",\"구성\":[]", "");
        server.expect(requestTo("http://ai.example/recommend"))
                .andRespond(withSuccess(missingProducts, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> adapter.recommend(request()))
                .isInstanceOfSatisfying(AiRecommendationException.class,
                        exception -> assertThat(exception.failureCode()).isEqualTo("AI_INVALID_RESPONSE"));
    }

    @Test
    void rejectsNullElementsInOptionalTextListsAsInvalidAiOutput() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://ai.example");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        FastApiRecommendationAdapter adapter = new FastApiRecommendationAdapter(builder.build(), objectMapper);
        String invalidReasons = response(0).replace("[\"AI 분석 소견\"]", "[null]");
        server.expect(requestTo("http://ai.example/recommend"))
                .andRespond(withSuccess(invalidReasons, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> adapter.recommend(request()))
                .isInstanceOfSatisfying(AiRecommendationException.class,
                        exception -> assertThat(exception.failureCode()).isEqualTo("AI_INVALID_RESPONSE"));
    }

    @Test
    void distinguishesTimeoutFromOtherHttpFailures() {
        RestClient restClient = RestClient.builder()
                .requestFactory((uri, method) -> {
                    throw new SocketTimeoutException("read timed out");
                })
                .build();
        FastApiRecommendationAdapter adapter = new FastApiRecommendationAdapter(restClient, objectMapper);

        assertThatThrownBy(() -> adapter.recommend(request()))
                .isInstanceOfSatisfying(AiRecommendationException.class,
                        exception -> assertThat(exception.failureCode()).isEqualTo("AI_TIMEOUT"));
    }

    private void assertFailure(org.springframework.test.web.client.ResponseCreator response, String code) {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://ai.example");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        FastApiRecommendationAdapter adapter = new FastApiRecommendationAdapter(builder.build(), objectMapper);
        server.expect(requestTo("http://ai.example/recommend")).andRespond(response);

        assertThatThrownBy(() -> adapter.recommend(request()))
                .isInstanceOfSatisfying(AiRecommendationException.class,
                        exception -> assertThat(exception.failureCode()).isEqualTo(code));
        server.verify();
    }

    private AiRecommendationRequest request() {
        return new AiRecommendationRequest(
                objectMapper.readTree("{\"predicted_label\":\"normal\",\"confidence\":0.91}"),
                null,
                objectMapper.readTree("{\"skinType\":1,\"concerns\":[4]}"),
                50_000L,
                0.6);
    }

    private String response(int productCount) {
        StringBuilder products = new StringBuilder();
        for (int index = 1; index <= productCount; index++) {
            if (index > 1) products.append(',');
            products.append("""
                    {"순서":%d,"슬롯":"%s","goods_no":"A%d","brand":"Brand","name":"Product",
                     "일일가격":1000,"일일용량":"2ml","전체용량":"200ml","판매가":10000,"추천이유":null,
                     "적합도":{"저자극":68.0},"적합도출처":"실측","고시":null,"무향":true,"코메도":0}
                    """.formatted(index, switch (index) {
                        case 1 -> "토너";
                        case 2 -> "로션";
                        case 3 -> "크림";
                        case 4 -> "클렌저/리무버";
                        case 5 -> "에센스/세럼";
                        case 6 -> "선케어";
                        default -> "미스트/특수케어";
                    }, index));
        }
        return """
                {"진단":"normal","헤드라인":"정상 피부","요약":"기본 관리","신뢰도":0.91,"트리아지":"normal",
                 "의료상담권고":true,"의료권고사유":["AI 분석 소견"],"반영된설문":null,"총액_일일":%d,"구성":[%s]}
                """.formatted(productCount * 1_000, products);
    }
}
