package com.jaungangton.api.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@TestPropertySource(properties = {
        "centralton.dev.auth.enabled=true",
        "centralton.dev.auth.header-value=test-dev-header"
})
class DevMockGoogleAuthApiIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired RefreshSessionRepository refreshSessions;
    @Autowired OAuthIdentityRepository identities;
    @Autowired UserRepository users;

    @BeforeEach
    void cleanAuthData() {
        refreshSessions.deleteAll();
        identities.deleteAll();
        users.deleteAll();
    }

    @Test
    void issuesNormalSessionOnlyWithConfiguredHeader() throws Exception {
        mockMvc.perform(post("/api/v1/dev/auth/mock-google")
                        .header("X-Centralton-Dev-Auth", "wrong"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_DEV_AUTH"));

        mockMvc.perform(post("/api/v1/dev/auth/mock-google")
                        .header("X-Centralton-Dev-Auth", "test-dev-header"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isNewUser").value(true))
                .andExpect(jsonPath("$.onboardingStatus").value("PROFILE_REQUIRED"))
                .andExpect(jsonPath("$.user.email").value("centralton.dev@example.com"));
    }
}
