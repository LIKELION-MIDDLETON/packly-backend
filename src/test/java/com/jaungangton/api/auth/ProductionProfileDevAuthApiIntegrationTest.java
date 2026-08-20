package com.jaungangton.api.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("prod")
class ProductionProfileDevAuthApiIntegrationTest {
    @Autowired MockMvc mockMvc;

    @Test
    void mockGoogleRouteIsNotRegisteredOutsideDevelopmentProfiles() throws Exception {
        mockMvc.perform(post("/api/v1/dev/auth/mock-google"))
                .andExpect(status().isNotFound());
    }
}
