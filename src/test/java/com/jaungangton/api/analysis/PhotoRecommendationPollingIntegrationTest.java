package com.jaungangton.api.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.nullValue;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import com.jaungangton.api.ai.AiPhotoAnalysisExchange;
import com.jaungangton.api.ai.AiRecommendationExchange;
import com.jaungangton.api.ai.AiRecommendationPort;
import com.jaungangton.api.ai.AiRecommendationProduct;
import com.jaungangton.api.ai.AiRecommendationResult;
import com.jaungangton.api.auth.GoogleTokenVerifierPort;
import com.jaungangton.api.auth.OAuthIdentityRepository;
import com.jaungangton.api.auth.RefreshSessionRepository;
import com.jaungangton.api.auth.UserRepository;
import com.jaungangton.api.recommendation.RecommendationRepository;
import com.jaungangton.api.recommendation.RecommendationProductRepository;
import com.jaungangton.api.survey.SurveyRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@AutoConfigureMockMvc
class PhotoRecommendationPollingIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired RecommendationRepository recommendations;
    @Autowired RecommendationProductRepository products;
    @Autowired com.jaungangton.api.analysis.AnalysisJobRepository analyses;
    @Autowired SurveyRepository surveys;
    @Autowired RefreshSessionRepository refreshSessions;
    @Autowired OAuthIdentityRepository identities;
    @Autowired UserRepository users;

    @MockitoBean GoogleTokenVerifierPort googleVerifier;
    @MockitoBean PhotoAnalysisPort photoAnalysisPort;
    @MockitoBean AiRecommendationPort recommendationPort;

    @BeforeEach
    void setUp() {
        clearDatabase();
        when(googleVerifier.verify("photo-google-token"))
                .thenReturn(new GoogleTokenVerifierPort.VerifiedGoogleIdentity(
                        "photo-google-sub", "photo@example.com", true, "Photo User", null));
        when(photoAnalysisPort.analyze(any(byte[].class), eq("image/jpeg"), anyString(), eq(8)))
                .thenReturn(new AiPhotoAnalysisExchange(
                        objectMapper.readTree("{\"predicted_label\":\"normal\",\"confidence\":0.91,\"top_k\":[]}"),
                        objectMapper.readTree("{\"summary\":\"mock summary\"}"),
                        objectMapper.readTree("{\"skin_type\":1,\"concerns\":[4]}"),
                        "{\"cnn_result\":{}}"));
        when(recommendationPort.recommend(any())).thenReturn(recommendationExchange());
    }

    @AfterEach
    void tearDown() {
        clearDatabase();
    }

    @Test
    void uploadsOnePhotoPollsThroughAnalyzingAndPersistsRecommendationId() throws Exception {
        String accessToken = loginAndCompleteSurvey();
        String created = mockMvc.perform(post("/api/v1/analyses")
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Idempotency-Key", "photo-polling-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"budgetTotal\":50000}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("WAITING_FOR_PHOTO_ANALYSIS"))
                .andReturn().getResponse().getContentAsString();
        UUID analysisId = UUID.fromString(JsonPath.read(created, "$.id"));

        mockMvc.perform(multipart("/api/v1/analyses/{id}/photo", analysisId)
                        .file(new MockMultipartFile("image", "face.jpg", "image/jpeg",
                                new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x00}))
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("ANALYZING"))
                .andExpect(jsonPath("$.recommendationId").value(nullValue()));

        String completed = pollUntilCompleted(accessToken, analysisId);
        assertThat((String) JsonPath.read(completed, "$.status")).isEqualTo("COMPLETED");
        String recommendationId = JsonPath.read(completed, "$.recommendationId");
        assertThat(recommendationId).isNotBlank();

        mockMvc.perform(post("/api/v1/analyses")
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Idempotency-Key", "photo-polling-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.recommendationId").value(recommendationId));

        mockMvc.perform(get("/api/v1/recommendations/{id}", recommendationId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analysisId").value(analysisId.toString()))
                .andExpect(jsonPath("$.products[0].order").value(1))
                .andExpect(jsonPath("$.products[0].displayOrder").value(1))
                .andExpect(jsonPath("$.products[0].applicationOrder").value(1))
                .andExpect(jsonPath("$.products[0].usageGroup").value("CORE_ROUTINE"))
                .andExpect(jsonPath("$.products[0].dailyPrice").value(100))
                .andExpect(jsonPath("$.products[0].dailyVolume").value("2ml"))
                .andExpect(jsonPath("$.products[0].salePrice").value(1001))
                .andExpect(jsonPath("$.totalPriceDaily").value(600));
        mockMvc.perform(get("/api/v1/recommendations/{id}", recommendationId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(jsonPath("$.products").isArray())
                .andExpect(jsonPath("$.products").isNotEmpty())
                .andExpect(jsonPath("$.products[2].slot").value("논코메도닉 수분젤"))
                .andExpect(jsonPath("$.products[2].applicationOrder").value(3))
                .andExpect(jsonPath("$.products[3].displayOrder").value(4))
                .andExpect(jsonPath("$.products[3].order").value(4))
                .andExpect(jsonPath("$.products[3].applicationOrder").value(nullValue()))
                .andExpect(jsonPath("$.products[3].usageGroup").value("CLEANSE"));

        verify(photoAnalysisPort, times(1)).analyze(any(byte[].class), eq("image/jpeg"), anyString(), anyInt());
        verify(recommendationPort, times(1)).recommend(any());
        assertThat(recommendations.findByAnalysisId(analysisId)).isPresent();
        assertThat(products.count()).isEqualTo(7);
        assertThat(jdbc.queryForObject(
                "select total_price_daily from recommendations where analysis_id = ?",
                Long.class, analysisId)).isEqualTo(600L);
        assertThat(jdbc.queryForObject(
                "select daily_price from recommendation_products where recommendation_id = ? and display_order = 1",
                Long.class, UUID.fromString(recommendationId))).isEqualTo(100L);
        assertThat(jdbc.queryForObject(
                "select sale_price from recommendation_products where recommendation_id = ? and display_order = 6",
                Long.class, UUID.fromString(recommendationId))).isEqualTo(1006L);
        assertThat(jdbc.queryForObject(
                "select daily_price from recommendation_products where recommendation_id = ? and display_order = 6",
                Long.class, UUID.fromString(recommendationId))).isNull();
    }

    @Test
    void completedUserCanRunAnotherPhotoAnalysis() throws Exception {
        String accessToken = loginAndCompleteSurvey();

        UUID firstAnalysisId = createAnalysis(accessToken, "repeat-analysis-first");
        uploadPhoto(accessToken, firstAnalysisId);
        pollUntilCompleted(accessToken, firstAnalysisId);

        UUID secondAnalysisId = createAnalysis(accessToken, "repeat-analysis-second");
        uploadPhoto(accessToken, secondAnalysisId);
        String completed = pollUntilCompleted(accessToken, secondAnalysisId);

        assertThat((String) JsonPath.read(completed, "$.status")).isEqualTo("COMPLETED");
        assertThat((String) JsonPath.read(completed, "$.recommendationId")).isNotBlank();
        verify(photoAnalysisPort, times(2)).analyze(any(byte[].class), eq("image/jpeg"), anyString(), eq(8));
        verify(recommendationPort, times(2)).recommend(any());
    }

    @Test
    void rejectsAnalysisUploadAndPollingForAnotherUserWithNotFound() throws Exception {
        String ownerToken = loginAndCompleteSurvey();
        String created = mockMvc.perform(post("/api/v1/analyses")
                        .header("Authorization", "Bearer " + ownerToken)
                        .header("Idempotency-Key", "ownership-key")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        UUID analysisId = UUID.fromString(JsonPath.read(created, "$.id"));

        when(googleVerifier.verify("other-google-token"))
                .thenReturn(new GoogleTokenVerifierPort.VerifiedGoogleIdentity(
                        "other-google-sub", "other@example.com", true, "Other User", null));
        String otherToken = loginAndCompleteSurvey("other-google-token", "Other User");
        MockMultipartFile photo = new MockMultipartFile("image", "face.jpg", "image/jpeg",
                new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff});

        mockMvc.perform(multipart("/api/v1/analyses/{id}/photo", analysisId)
                        .file(photo).header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ANALYSIS_NOT_FOUND"));
        mockMvc.perform(get("/api/v1/analyses/{id}", analysisId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ANALYSIS_NOT_FOUND"));
    }

    @Test
    void rejectsPhotoLargerThanTenMegabytesBeforeStartingTheWorker() throws Exception {
        String accessToken = loginAndCompleteSurvey();
        String created = mockMvc.perform(post("/api/v1/analyses")
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Idempotency-Key", "photo-size-key")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        UUID analysisId = UUID.fromString(JsonPath.read(created, "$.id"));
        byte[] tooLarge = new byte[PhotoAnalysisWorkflowService.MAX_PHOTO_BYTES + 1];

        mockMvc.perform(multipart("/api/v1/analyses/{id}/photo", analysisId)
                        .file(new MockMultipartFile("image", "large.jpg", "image/jpeg", tooLarge))
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("PHOTO_TOO_LARGE"));
        mockMvc.perform(get("/api/v1/analyses/{id}", analysisId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WAITING_FOR_PHOTO_ANALYSIS"));
        verify(photoAnalysisPort, times(0)).analyze(any(byte[].class), anyString(), anyString(), anyInt());
    }

    @Test
    void acceptsPhotoAtTheTenMegabyteBoundary() throws Exception {
        String accessToken = loginAndCompleteSurvey();
        String created = mockMvc.perform(post("/api/v1/analyses")
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Idempotency-Key", "photo-boundary-key")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        UUID analysisId = UUID.fromString(JsonPath.read(created, "$.id"));
        byte[] exact = new byte[PhotoAnalysisWorkflowService.MAX_PHOTO_BYTES];
        exact[0] = (byte) 0xff;
        exact[1] = (byte) 0xd8;
        exact[2] = (byte) 0xff;

        mockMvc.perform(multipart("/api/v1/analyses/{id}/photo", analysisId)
                        .file(new MockMultipartFile("image", "boundary.jpg", "image/jpeg", exact))
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isAccepted());
        pollUntilCompleted(accessToken, analysisId);
        verify(photoAnalysisPort, times(1)).analyze(
                any(byte[].class), eq("image/jpeg"), anyString(), eq(8));
    }

    @Test
    void serializesZeroProductRecommendationWithTheCompletePublicShape() throws Exception {
        when(recommendationPort.recommend(any())).thenReturn(emptyRecommendationExchange());
        String accessToken = loginAndCompleteSurvey();
        String created = mockMvc.perform(post("/api/v1/analyses")
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Idempotency-Key", "zero-product-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        UUID analysisId = UUID.fromString(JsonPath.read(created, "$.id"));

        mockMvc.perform(multipart("/api/v1/analyses/{id}/photo", analysisId)
                        .file(new MockMultipartFile("image", "face.jpg", "image/jpeg",
                                new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff}))
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isAccepted());

        String completed = pollUntilCompleted(accessToken, analysisId);
        String recommendationId = JsonPath.read(completed, "$.recommendationId");
        mockMvc.perform(get("/api/v1/recommendations/{id}", recommendationId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(recommendationId))
                .andExpect(jsonPath("$.analysisId").value(analysisId.toString()))
                .andExpect(jsonPath("$.medicalAdvice.recommended").value(false))
                .andExpect(jsonPath("$.medicalAdvice.reasons").isArray())
                .andExpect(jsonPath("$.reflectedSurvey").value(nullValue()))
                .andExpect(jsonPath("$.products").isArray())
                .andExpect(jsonPath("$.products").isEmpty())
                .andExpect(jsonPath("$.totalPrice").value(0))
                .andExpect(jsonPath("$.analysisSummary").value(nullValue()))
                .andExpect(jsonPath("$.careRecommendations").isArray())
                .andExpect(jsonPath("$.disclaimer").value(nullValue()));

        assertThat(products.count()).isZero();
        assertThat(jdbc.queryForObject(
                "select total_price_daily from recommendations where analysis_id = ?",
                Long.class, analysisId)).isZero();
    }

    @Test
    void providerFailureIsStoredWithoutExposingProviderDetails() throws Exception {
        when(photoAnalysisPort.analyze(any(byte[].class), anyString(), anyString(), anyInt()))
                .thenThrow(new com.jaungangton.api.ai.AiAnalysisException(
                        "AI_ANALYSIS_SERVER_ERROR", "provider URL should not escape"));
        String accessToken = loginAndCompleteSurvey();
        String created = mockMvc.perform(post("/api/v1/analyses")
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Idempotency-Key", "photo-failure-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        UUID analysisId = UUID.fromString(JsonPath.read(created, "$.id"));

        mockMvc.perform(multipart("/api/v1/analyses/{id}/photo", analysisId)
                        .file(new MockMultipartFile("image", "face.jpg", "image/jpeg",
                                new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff}))
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isAccepted());

        String failed = pollUntilStatus(accessToken, analysisId, "FAILED");
        assertThat((String) JsonPath.read(failed, "$.failureCode")).isEqualTo("AI_ANALYSIS_SERVER_ERROR");
        assertThat(failed).doesNotContain("provider URL should not escape");
        assertThat(recommendations.findByAnalysisId(analysisId)).isEmpty();
    }

    @Test
    void recommendationTimeoutMovesTheJobToFailedWithOnlyAStableCode() throws Exception {
        when(recommendationPort.recommend(any())).thenThrow(
                new com.jaungangton.api.ai.AiRecommendationException(
                        "AI_TIMEOUT", "provider URL and timeout details must not escape"));
        String accessToken = loginAndCompleteSurvey();
        String created = mockMvc.perform(post("/api/v1/analyses")
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Idempotency-Key", "recommendation-timeout-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        UUID analysisId = UUID.fromString(JsonPath.read(created, "$.id"));

        mockMvc.perform(multipart("/api/v1/analyses/{id}/photo", analysisId)
                        .file(new MockMultipartFile("image", "face.jpg", "image/jpeg",
                                new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff}))
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isAccepted());

        String failed = pollUntilStatus(accessToken, analysisId, "FAILED");
        assertThat((String) JsonPath.read(failed, "$.failureCode")).isEqualTo("AI_TIMEOUT");
        assertThat(failed).doesNotContain("provider URL and timeout details must not escape");
        assertThat(recommendations.findByAnalysisId(analysisId)).isEmpty();
    }

    @Test
    void rejectsMultiplePartsAndMimeSpoofBeforeMovingTheJob() throws Exception {
        String accessToken = loginAndCompleteSurvey();
        String created = mockMvc.perform(post("/api/v1/analyses")
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Idempotency-Key", "photo-validation-key")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        UUID analysisId = UUID.fromString(JsonPath.read(created, "$.id"));
        MockMultipartFile jpeg = new MockMultipartFile("image", "face.jpg", "image/jpeg",
                new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff});
        MockMultipartFile jpgAlias = new MockMultipartFile("image", "face.jpg", "image/jpg",
                jpeg.getBytes());

        mockMvc.perform(multipart("/api/v1/analyses/{id}/photo", analysisId)
                        .file(jpeg).file(jpeg).header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ONE_PHOTO_REQUIRED"));
        mockMvc.perform(multipart("/api/v1/analyses/{id}/photo", analysisId)
                        .file(new MockMultipartFile("image", "face.png", "image/png", jpeg.getBytes()))
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PHOTO_CONTENT"));
        mockMvc.perform(multipart("/api/v1/analyses/{id}/photo", analysisId)
                        .file(jpgAlias)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isAccepted());
        pollUntilCompleted(accessToken, analysisId);
        mockMvc.perform(multipart("/api/v1/analyses/{id}/photo", analysisId)
                        .file(jpeg)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.recommendationId").isNotEmpty());
        mockMvc.perform(get("/api/v1/analyses/{id}", analysisId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
        verify(photoAnalysisPort, times(1)).analyze(any(byte[].class), eq("image/jpeg"), anyString(), eq(8));
    }

    @Test
    void returnsBadRequestForNonMultipartPhotoRequests() throws Exception {
        String accessToken = loginAndCompleteSurvey();
        String created = mockMvc.perform(post("/api/v1/analyses")
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Idempotency-Key", "malformed-multipart-key")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        UUID analysisId = UUID.fromString(JsonPath.read(created, "$.id"));

        mockMvc.perform(post("/api/v1/analyses/{id}/photo", analysisId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_MULTIPART"));
    }

    private String loginAndCompleteSurvey() throws Exception {
        return loginAndCompleteSurvey("photo-google-token", "Photo User");
    }

    private String loginAndCompleteSurvey(String idToken, String nickname) throws Exception {
        String login = mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"" + idToken + "\",\"termsAccepted\":true}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String accessToken = JsonPath.read(login, "$.accessToken");
        mockMvc.perform(put("/api/v1/me/profile")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"" + nickname + "\",\"postalCode\":\"12345\",\"addressLine1\":\"서울시 중구\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/me/survey")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"skinType":"DRY","concerns":["DRYNESS_FLAKING"],"duration":"UP_TO_ONE_WEEK",
                                 "areas":["CHEEKS"],"irritation":"NEVER","diagnosed":"NONE"}
                                """))
                .andExpect(status().isOk());
        return accessToken;
    }

    private UUID createAnalysis(String accessToken, String idempotencyKey) throws Exception {
        String created = mockMvc.perform(post("/api/v1/analyses")
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(created, "$.id"));
    }

    private void uploadPhoto(String accessToken, UUID analysisId) throws Exception {
        mockMvc.perform(multipart("/api/v1/analyses/{id}/photo", analysisId)
                        .file(new MockMultipartFile("image", "face.jpg", "image/jpeg",
                                new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x00}))
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("ANALYZING"));
    }

    private String pollUntilCompleted(String accessToken, UUID analysisId) throws Exception {
        return pollUntilStatus(accessToken, analysisId, "COMPLETED");
    }

    private String pollUntilStatus(String accessToken, UUID analysisId, String expectedStatus) throws Exception {
        String latest = null;
        for (int attempt = 0; attempt < 40; attempt++) {
            latest = mockMvc.perform(get("/api/v1/analyses/{id}", analysisId)
                            .header("Authorization", "Bearer " + accessToken))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            if (expectedStatus.equals(JsonPath.read(latest, "$.status"))) {
                return latest;
            }
            Thread.sleep(50L);
        }
        throw new AssertionError("Timed out waiting for " + expectedStatus + ": " + latest);
    }

    private AiRecommendationExchange recommendationExchange() {
        List<AiRecommendationProduct> products = IntStream.rangeClosed(1, 7)
                .mapToObj(index -> new AiRecommendationProduct(
                        index, index <= 3 ? index : null,
                        index <= 3 ? "CORE_ROUTINE"
                                : switch (index) {
                                    case 4 -> "CLEANSE";
                                    case 5 -> "TREATMENT";
                                    case 6 -> "PROTECT";
                                    default -> "OCCASIONAL";
                                },
                        slot(index), "goods-" + index, "Brand", "Product " + index, (long) (1000 + index),
                        objectMapper.readTree("{\"lowIrritation\":92.0}"), "MEASURED", null, true, 0,
                        index == 6 ? null : 100L, index == 6 ? null : "2ml", "100ml",
                        (long) (1000 + index), null))
                .toList();
        long legacyTotal = products.stream().mapToLong(product -> product.price()).sum();
        long dailyTotal = products.stream().mapToLong(product ->
                product.dailyPrice() == null ? 0L : product.dailyPrice()).sum();
        AiRecommendationResult result = new AiRecommendationResult(
                "normal", "기본 루틴", "기본 관리", 0.91, "NORMAL", false, List.of(), null,
                products, legacyTotal, dailyTotal, null, List.of(), null);
        return new AiRecommendationExchange(result, "{\"request\":true}", "{\"response\":true}");
    }

    private String slot(int index) {
        return switch (index) {
            case 1 -> "토너";
            case 2 -> "로션";
            case 3 -> "논코메도닉 수분젤";
            case 4 -> "클렌저/리무버";
            case 5 -> "에센스/세럼";
            case 6 -> "선케어";
            default -> "미스트/특수케어";
        };
    }

    private AiRecommendationExchange emptyRecommendationExchange() {
        AiRecommendationResult result = new AiRecommendationResult(
                "normal", "기본 루틴", "기본 관리", 0.91, "NORMAL", false, List.of(), null,
                List.of(), 0L, 0L, null, List.of(), null);
        return new AiRecommendationExchange(result, "{\"request\":true}", "{\"response\":true}");
    }

    private void clearDatabase() {
        jdbc.update("delete from product_usage_completions");
        jdbc.update("delete from recommendation_feedback");
        jdbc.update("delete from sos_reports");
        products.deleteAll();
        recommendations.deleteAll();
        analyses.deleteAll();
        surveys.deleteAll();
        refreshSessions.deleteAll();
        identities.deleteAll();
        users.deleteAll();
    }
}
