package com.jaungangton.api.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.jaungangton.api.ai.AiRecommendationExchange;
import com.jaungangton.api.ai.AiRecommendationPort;
import com.jaungangton.api.ai.AiRecommendationProduct;
import com.jaungangton.api.ai.AiRecommendationResult;
import com.jaungangton.api.analysis.AnalysisJob;
import com.jaungangton.api.analysis.AnalysisJobRepository;
import com.jaungangton.api.analysis.AnalysisStatus;
import com.jaungangton.api.auth.GoogleTokenVerifierPort;
import com.jaungangton.api.auth.OAuthIdentityRepository;
import com.jaungangton.api.auth.RefreshSessionRepository;
import com.jaungangton.api.auth.UserRepository;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@AutoConfigureMockMvc
class RecommendationCompletionIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired AnalysisJobRepository analyses;
    @Autowired RecommendationRepository recommendations;
    @Autowired RecommendationProductRepository products;
    @Autowired SurveyRepository surveys;
    @Autowired RefreshSessionRepository refreshSessions;
    @Autowired OAuthIdentityRepository identities;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;

    @MockitoBean GoogleTokenVerifierPort googleVerifier;
    @MockitoBean AiRecommendationPort aiPort;
    @MockitoBean ProductImageProvider productImageProvider;

    @BeforeEach
    void setUp() {
        clearDatabase();
        when(googleVerifier.verify("integration-google-token"))
                .thenReturn(new GoogleTokenVerifierPort.VerifiedGoogleIdentity(
                        "integration-google-sub", "integration@example.com", true,
                        "Integration User", "https://example.com/avatar.png"));
        when(googleVerifier.verify("other-google-token"))
                .thenReturn(new GoogleTokenVerifierPort.VerifiedGoogleIdentity(
                        "other-google-sub", "other@example.com", true,
                        "Other User", null));
        when(aiPort.recommend(any())).thenReturn(exchange());
        when(productImageProvider.findImageUrl("Brand", "Lotion"))
                .thenReturn(Optional.of("https://search.example.test/lotion.jpg"));
    }

    @AfterEach
    void tearDown() {
        clearDatabase();
    }

    @Test
    void trustedCallbackPersistsOneRecommendationAndCompletesAnalysisIdempotently() throws Exception {
        String login = mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"integration-google-token\",\"termsAccepted\":true}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String accessToken = JsonPath.read(login, "$.accessToken");

        mockMvc.perform(put("/api/v1/me/profile")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"Integration User\",\"postalCode\":\"12345\","
                                + "\"addressLine1\":\"서울시 중구\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/me/survey")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "skinType":"DRY",
                                  "concerns":["DRYNESS_FLAKING"],
                                  "duration":"UP_TO_ONE_WEEK",
                                  "areas":["CHEEKS"],
                                  "irritation":"NEVER",
                                  "diagnosed":"NONE"
                                }
                                """))
                .andExpect(status().isOk());

        String created = mockMvc.perform(post("/api/v1/analyses")
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Idempotency-Key", "completion-integration-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"budgetTotal\":50000}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("WAITING_FOR_PHOTO_ANALYSIS"))
                .andReturn().getResponse().getContentAsString();
        UUID analysisId = UUID.fromString(JsonPath.read(created, "$.id"));

        String callbackBody = """
                {
                  "sourceResultId":"completion-source-1",
                  "cnnResult":{"predicted_label":"normal","confidence":0.91}
                }
                """;
        String recommendationJson = mockMvc.perform(post("/api/v1/internal/analyses/{id}/cnn-result", analysisId)
                        .header("X-Internal-Callback-Key", "test-internal-callback-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(callbackBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analysisId").value(analysisId.toString()))
                .andExpect(jsonPath("$.products.length()").value(1))
                .andExpect(jsonPath("$.products[0].imageUrl")
                        .value("https://search.example.test/lotion.jpg"))
                .andReturn().getResponse().getContentAsString();
        UUID recommendationId = UUID.fromString(JsonPath.read(recommendationJson, "$.id"));
        UUID productId = UUID.fromString(JsonPath.read(recommendationJson, "$.products[0].id"));

        AnalysisJob completed = analyses.findById(analysisId).orElseThrow();
        assertThat(completed.status()).isEqualTo(AnalysisStatus.COMPLETED);
        assertThat(jdbc.queryForObject(
                "select cnn_result_json from analysis_jobs where id = ?", String.class, analysisId))
                .isEqualTo("{\"predicted_label\":\"normal\",\"confidence\":0.91}");
        assertThat(jdbc.queryForObject(
                "select llm_result_json from analysis_jobs where id = ?", String.class, analysisId)).isNull();
        assertThat(jdbc.queryForObject(
                "select survey_result_json from analysis_jobs where id = ?", String.class, analysisId)).isNull();
        assertThat(recommendations.findByAnalysisId(analysisId)).isPresent();
        assertThat(recommendations.count()).isOne();
        assertThat(products.count()).isOne();
        assertThat(jdbc.queryForObject(
                "select image_url from recommendation_products where id = ?", String.class, productId))
                .isEqualTo("https://search.example.test/lotion.jpg");

        mockMvc.perform(post("/api/v1/internal/analyses/{id}/cnn-result", analysisId)
                        .header("X-Internal-Callback-Key", "test-internal-callback-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(callbackBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analysisId").value(analysisId.toString()));

        assertThat(recommendations.count()).isOne();
        assertThat(products.count()).isOne();
        verify(aiPort, times(1)).recommend(any());

        verifyEngagementIdempotencyAndOwnership(accessToken, recommendationId, productId);
    }

    private AiRecommendationExchange exchange() {
        AiRecommendationProduct product = new AiRecommendationProduct(
                1, "LOTION", "goods-1", "Brand", "Lotion", 18000,
                objectMapper.readTree("{\"lowIrritation\":92.0}"), "MEASURED",
                "barrier care", true, 1);
        AiRecommendationResult result = new AiRecommendationResult(
                "dryness", "Keep the barrier calm", "A gentle routine is recommended.",
                0.91, "NORMAL", false, List.of(),
                objectMapper.readTree("{\"skinType\":1,\"concerns\":[4]}"),
                List.of(product), 18000, "analysis summary", List.of("Use lukewarm water"),
                "This is not a medical diagnosis.");
        return new AiRecommendationExchange(result, "{\"request\":true}", "{\"response\":true}");
    }

    private void verifyEngagementIdempotencyAndOwnership(
            String ownerAccessToken, UUID recommendationId, UUID productId) throws Exception {
        String usageBody = "{\"usedOn\":\"2026-08-19\",\"completed\":true}";
        for (int invocation = 0; invocation < 2; invocation++) {
            mockMvc.perform(put("/api/v1/recommendations/{recommendationId}/products/{productId}/usage",
                            recommendationId, productId)
                            .header("Authorization", "Bearer " + ownerAccessToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(usageBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.completed").value(true));
        }
        for (int invocation = 0; invocation < 2; invocation++) {
            mockMvc.perform(put("/api/v1/recommendations/{recommendationId}/feedback", recommendationId)
                            .header("Authorization", "Bearer " + ownerAccessToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"rating\":5,\"comment\":\"gentle\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.rating").value(5));
        }
        mockMvc.perform(post("/api/v1/sos-reports")
                        .header("Authorization", "Bearer " + ownerAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recommendationId\":\"" + recommendationId
                                + "\",\"message\":\"Skin feels hot\",\"symptomLabels\":[\"HEAT\"]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("RECEIVED"));

        assertThat(rowCount("product_usage_completions")).isOne();
        assertThat(rowCount("recommendation_feedback")).isOne();
        assertThat(rowCount("sos_reports")).isOne();

        String otherLogin = mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"other-google-token\",\"termsAccepted\":true}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String otherAccessToken = JsonPath.read(otherLogin, "$.accessToken");

        mockMvc.perform(get("/api/v1/recommendations/{recommendationId}", recommendationId)
                        .header("Authorization", "Bearer " + otherAccessToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/v1/recommendations/{recommendationId}/products/{productId}/usage",
                        recommendationId, productId)
                        .header("Authorization", "Bearer " + otherAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(usageBody))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/v1/recommendations/{recommendationId}/feedback", recommendationId)
                        .header("Authorization", "Bearer " + otherAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":1}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/sos-reports")
                        .header("Authorization", "Bearer " + otherAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recommendationId\":\"" + recommendationId
                                + "\",\"message\":\"not mine\"}"))
                .andExpect(status().isNotFound());
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

    private long rowCount(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Long.class);
    }
}
