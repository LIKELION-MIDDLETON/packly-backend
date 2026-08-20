package com.jaungangton.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.jaungangton.api.common.ApiException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class AuthApiIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired UserRepository users;
    @Autowired OAuthIdentityRepository identities;
    @Autowired RefreshSessionRepository refreshSessions;
    @Autowired JwtDecoder jwtDecoder;
    @Autowired AuthService authService;
    @MockitoBean GoogleTokenVerifierPort googleVerifier;

    @BeforeEach
    void cleanAuthData() {
        refreshSessions.deleteAll();
        identities.deleteAll();
        users.deleteAll();
    }

    @Test
    void newGoogleUserRequiresTermsThenSameSubjectReturnsExistingUser() throws Exception {
        verified("google-token", "google-subject", true);

        mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"google-token\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.instance").value("/api/v1/auth/google"))
                .andExpect(jsonPath("$.code").value("TERMS_ACCEPTANCE_REQUIRED"))
                .andExpect(jsonPath("$.fieldErrors").isMap())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());

        String first = mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"google-token\",\"termsAccepted\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isNewUser").value(true))
                .andExpect(jsonPath("$.onboardingStatus").value("PROFILE_REQUIRED"))
                .andExpect(jsonPath("$.user.nickname").doesNotExist())
                .andExpect(jsonPath("$.user.email").value("person@example.com"))
                .andReturn().getResponse().getContentAsString();

        String second = mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"google-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isNewUser").value(false))
                .andReturn().getResponse().getContentAsString();

        assertThat(users.count()).isOne();
        assertThat(identities.count()).isOne();
        assertThat(JsonPath.<String>read(first, "$.user.id"))
                .isEqualTo(JsonPath.<String>read(second, "$.user.id"));
    }

    @Test
    void rejectsUnverifiedEmailAndProtectsMeWithCentraltonJwt() throws Exception {
        verified("unverified", "sub-a", false);
        mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"unverified\",\"termsAccepted\":true}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNVERIFIED_GOOGLE_IDENTITY"));

        mockMvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.instance").value("/api/v1/me"))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        verified("valid", "sub-b", true);
        String response = googleLogin("valid");
        String accessToken = JsonPath.read(response, "$.accessToken");
        String userId = JsonPath.read(response, "$.user.id");

        Jwt jwt = jwtDecoder.decode(accessToken);
        assertThat(jwt.getSubject()).isEqualTo(userId);
        assertThat(jwt.getAudience()).containsExactly("centralton-mobile");
        assertThat(jwt.getClaimAsString("role")).isEqualTo("USER");
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("USER");
        assertThat(jwt.getId()).isNotBlank();
        assertThat(jwt.getClaimAsString("sid")).isNotBlank();

        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId));
    }

    @Test
    void rotatesRefreshTokenDetectsReuseAndRevokesWholeFamily() throws Exception {
        verified("valid", "sub-c", true);
        String login = googleLogin("valid");
        String oldRefresh = JsonPath.read(login, "$.refreshToken");

        String rotated = refresh(oldRefresh, 200);
        String newRefresh = JsonPath.read(rotated, "$.refreshToken");
        assertThat(newRefresh).isNotEqualTo(oldRefresh);

        refresh(oldRefresh, 401);
        refresh(newRefresh, 401);
        assertThat(refreshSessions.findAll()).allMatch(session -> session.getRevokedAt() != null);
    }

    @Test
    void concurrentRefreshIsSerializedAndReuseRevokesTheTokenFamily() throws Exception {
        verified("valid", "sub-concurrent", true);
        String login = googleLogin("valid");
        String oldRefresh = JsonPath.read(login, "$.refreshToken");
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> first = executor.submit(() -> refreshStatusAfter(start, oldRefresh));
            Future<Integer> second = executor.submit(() -> refreshStatusAfter(start, oldRefresh));
            start.countDown();

            assertThat(List.of(first.get(), second.get()))
                    .containsExactlyInAnyOrder(200, 401);
            assertThat(refreshSessions.findAll())
                    .hasSize(2)
                    .allMatch(session -> session.getRevokedAt() != null);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentFirstGoogleExchangeReturnsTheSameMemberWithoutDuplicates() throws Exception {
        verified("concurrent-google", "concurrent-google-sub", true);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<AuthPersistenceService.AuthResult> first = executor.submit(() -> {
                start.await();
                return authService.exchangeGoogle("concurrent-google", true);
            });
            Future<AuthPersistenceService.AuthResult> second = executor.submit(() -> {
                start.await();
                return authService.exchangeGoogle("concurrent-google", true);
            });
            start.countDown();

            List<AuthPersistenceService.AuthResult> results = List.of(first.get(), second.get());
            assertThat(results).extracting(result -> result.user().getId()).containsOnly(results.get(0).user().getId());
            assertThat(Stream.of(results.get(0).isNewUser(), results.get(1).isNewUser()).filter(Boolean::booleanValue))
                    .hasSize(1);
            assertThat(users.count()).isOne();
            assertThat(identities.count()).isOne();
            assertThat(refreshSessions.count()).isEqualTo(2);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void logoutRevokesRefreshFamily() throws Exception {
        verified("valid", "sub-d", true);
        String login = googleLogin("valid");
        String access = JsonPath.read(login, "$.accessToken");
        String refresh = JsonPath.read(login, "$.refreshToken");

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refresh + "\"}"))
                .andExpect(status().isNoContent());
        refresh(refresh, 401);
    }

    @Test
    void profileIsRequiredBeforeSurveyAndIsReturnedConsistently() throws Exception {
        verified("valid", "sub-profile", true);
        String login = googleLogin("valid");
        String access = JsonPath.read(login, "$.accessToken");

        mockMvc.perform(put("/api/v1/me/survey")
                        .header("Authorization", "Bearer " + access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validSurveyJson()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROFILE_REQUIRED"));

        mockMvc.perform(put("/api/v1/me/profile")
                        .header("Authorization", "Bearer " + access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"  Alice  Smith \",\"postalCode\":\"12345\","
                                + "\"addressLine1\":\"서울시 중구 세종대로 1\",\"addressLine2\":\"101호\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("Alice  Smith"))
                .andExpect(jsonPath("$.postalCode").value("12345"))
                .andExpect(jsonPath("$.addressLine1").value("서울시 중구 세종대로 1"))
                .andExpect(jsonPath("$.addressLine2").value("101호"))
                .andExpect(jsonPath("$.onboardingStatus").value("SURVEY_REQUIRED"));

        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("Alice  Smith"))
                .andExpect(jsonPath("$.onboardingStatus").value("SURVEY_REQUIRED"));

        mockMvc.perform(put("/api/v1/me/survey")
                        .header("Authorization", "Bearer " + access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validSurveyJson()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onboardingStatus").value("PHOTO_REQUIRED"));
    }

    @Test
    void normalizedNicknameConflictReturns409() throws Exception {
        verified("first", "sub-first", true);
        String firstAccess = JsonPath.read(googleLogin("first"), "$.accessToken");
        saveProfile(firstAccess, "Same Name");

        verified("second", "sub-second", true);
        String secondAccess = JsonPath.read(googleLogin("second"), "$.accessToken");
        mockMvc.perform(put("/api/v1/me/profile")
                        .header("Authorization", "Bearer " + secondAccess)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\" same   name \",\"postalCode\":\"54321\","
                                + "\"addressLine1\":\"부산시 해운대구\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NICKNAME_ALREADY_IN_USE"));
    }

    private void verified(String token, String subject, boolean emailVerified) {
        when(googleVerifier.verify(token)).thenReturn(new GoogleTokenVerifierPort.VerifiedGoogleIdentity(
                subject, "person@example.com", emailVerified, "Person", "https://example.com/avatar.png"));
    }

    private String googleLogin(String token) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"" + token + "\",\"termsAccepted\":true}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private String refresh(String token, int expectedStatus) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + token + "\"}"))
                .andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString();
    }

    private int refreshStatusAfter(CountDownLatch start, String refreshToken) throws InterruptedException {
        start.await();
        try {
            authService.refresh(refreshToken);
            return 200;
        } catch (ApiException exception) {
            return exception.status().value();
        }
    }

    private void saveProfile(String accessToken, String nickname) throws Exception {
        mockMvc.perform(put("/api/v1/me/profile")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"" + nickname + "\",\"postalCode\":\"12345\","
                                + "\"addressLine1\":\"서울시 중구\"}"))
                .andExpect(status().isOk());
    }

    private String validSurveyJson() {
        return "{\"skinType\":\"DRY\",\"concerns\":[\"NONE\"],"
                + "\"duration\":\"NOT_APPLICABLE\",\"areas\":[\"NONE\"],"
                + "\"irritation\":\"NEVER\",\"diagnosed\":\"NONE\"}";
    }
}
