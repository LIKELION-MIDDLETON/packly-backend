package com.jaungangton.api.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.SocketTimeoutException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.hamcrest.Matchers.containsString;

import tools.jackson.databind.ObjectMapper;

class FastApiPhotoAnalysisAdapterTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void sendsMultipartImageAnswersAndFixedTopKAndReadsTheThreePartResponse() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://analysis.example");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        FastApiPhotoAnalysisAdapter adapter = new FastApiPhotoAnalysisAdapter(builder.build(), objectMapper);
        server.expect(requestTo("http://analysis.example/analyze"))
                .andExpect(content().string(containsString("name=\"image\"")))
                .andExpect(content().string(containsString("Content-Type: image/jpeg")))
                .andExpect(content().string(containsString("name=\"answers\"")))
                .andExpect(content().string(containsString("name=\"top_k\"")))
                .andExpect(content().string(containsString("skin_type")))
                .andExpect(content().string(containsString("8")))
                .andRespond(withSuccess("""
                        {"cnn_result":{"predicted_label":"normal","confidence":0.91,"top_k":[]},
                         "llm_result":{"summary":"summary"},"survey":{"skin_type":1}}
                        """, MediaType.APPLICATION_JSON));

        AiPhotoAnalysisExchange result = adapter.analyze(
                new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff}, "image/jpeg",
                "{\"skin_type\":\"건성\"}", 8);

        assertThat(result.cnnResult().get("predicted_label").asText()).isEqualTo("normal");
        assertThat(result.llmResult().get("summary").asText()).isEqualTo("summary");
        assertThat(result.survey().get("skin_type").asInt()).isEqualTo(1);
        server.verify();
    }

    @Test
    void productionHttpClientSendsAFastApiCompatibleMultipartBody() throws Exception {
        AtomicReference<String> contentType = new AtomicReference<>();
        AtomicReference<String> upgrade = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/analyze", exchange -> {
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            upgrade.set(exchange.getRequestHeaders().getFirst("Upgrade"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.ISO_8859_1));
            byte[] response = """
                    {"cnn_result":{"predicted_label":"normal","confidence":0.91,"top_k":[]},
                     "llm_result":null,"survey":null}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        try {
            FastApiPhotoAnalysisAdapter adapter = new FastApiPhotoAnalysisAdapter(
                    objectMapper,
                    "http://127.0.0.1:" + server.getAddress().getPort(),
                    Duration.ofSeconds(2),
                    Duration.ofSeconds(2));

            adapter.analyze(new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47}, "image/png",
                    "{\"skin_type\":\"건성\"}", 8);

            assertThat(contentType.get()).startsWith("multipart/form-data;boundary=");
            assertThat(upgrade.get()).isNull();
            assertThat(requestBody.get())
                    .contains("Content-Disposition: form-data; name=\"image\"; filename=\"photo-upload\"")
                    .contains("Content-Type: image/png")
                    .contains("Content-Disposition: form-data; name=\"answers\"")
                    .contains("Content-Disposition: form-data; name=\"top_k\"");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void mapsTimeoutToStableAnalysisFailureCode() {
        RestClient restClient = RestClient.builder()
                .requestFactory((uri, method) -> {
                    throw new SocketTimeoutException("read timed out");
                })
                .build();
        FastApiPhotoAnalysisAdapter adapter = new FastApiPhotoAnalysisAdapter(restClient, objectMapper);

        assertThatThrownBy(() -> adapter.analyze(new byte[] {1}, "image/jpeg", "{}", 8))
                .isInstanceOfSatisfying(AiAnalysisException.class,
                        exception -> assertThat(exception.failureCode()).isEqualTo("AI_ANALYSIS_TIMEOUT"));
    }
}
