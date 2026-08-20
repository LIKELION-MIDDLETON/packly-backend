package com.jaungangton.api.analysis;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;
import java.util.UUID;

import com.jaungangton.api.recommendation.RecommendationWorkflowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class InternalAnalysisControllerTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean AnalysisResultAcceptanceService acceptance;
    @MockitoBean RecommendationWorkflowService workflow;

    @BeforeEach
    void resetMocks() {
        reset(acceptance, workflow);
    }

    @Test
    void missingOrWrongInternalCredentialIsRejectedBeforeProcessing() throws Exception {
        UUID analysisId = UUID.randomUUID();
        String body = """
                {"sourceResultId":"source-1","cnnResult":{"predictedLabel":"normal","confidence":0.9}}
                """;

        mockMvc.perform(post("/api/v1/internal/analyses/{id}/cnn-result", analysisId)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_INTERNAL_CREDENTIAL"));
        mockMvc.perform(post("/api/v1/internal/analyses/{id}/cnn-result", analysisId)
                        .header("X-Internal-Callback-Key", "wrong")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(acceptance, workflow);
    }

    @Test
    void configuredInternalCredentialCanStartRecommendationWorkflow() throws Exception {
        UUID analysisId = UUID.randomUUID();
        RecommendationWork work = new RecommendationWork(
                analysisId, UUID.randomUUID(), "{}", null, "source-1", null, null, false);
        when(acceptance.acceptPhotoAnalysisResult(eq(analysisId), any())).thenReturn(work);
        when(workflow.process(work)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/v1/internal/analyses/{id}/cnn-result", analysisId)
                        .header("X-Internal-Callback-Key", "test-internal-callback-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sourceResultId":"source-1","cnnResult":{"predictedLabel":"normal","confidence":0.9}}
                                """))
                .andExpect(status().isAccepted());
    }
}
