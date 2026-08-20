package com.jaungangton.api.ai;

import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.jaungangton.api.analysis.PhotoAnalysisPort;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class FastApiPhotoAnalysisAdapter implements PhotoAnalysisPort {
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public FastApiPhotoAnalysisAdapter(
            ObjectMapper objectMapper,
            @Value("${centralton.ai.analysis-base-url}") String baseUrl,
            @Value("${centralton.ai.analysis-connect-timeout:2s}") Duration connectTimeout,
            @Value("${centralton.ai.analysis-read-timeout:30s}") Duration readTimeout) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
        this.objectMapper = objectMapper;
    }

    FastApiPhotoAnalysisAdapter(RestClient restClient, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public AiPhotoAnalysisExchange analyze(byte[] image, String contentType, String answersJson, int topK) {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        HttpHeaders imageHeaders = new HttpHeaders();
        imageHeaders.setContentType(MediaType.parseMediaType(contentType));
        imageHeaders.setContentDispositionFormData("image", "photo-upload");
        form.add("image", new HttpEntity<>(new NamedByteArrayResource(image, "photo-upload"), imageHeaders));
        form.add("answers", answersJson);
        form.add("top_k", String.valueOf(topK));
        try {
            String body = restClient.post()
                    .uri("/analyze")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(form)
                    .retrieve()
                    .body(String.class);
            if (body == null || body.isBlank()) {
                throw invalidResponse("AI analysis returned an empty response", null);
            }
            JsonNode response = read(body);
            JsonNode cnn = response.get("cnn_result");
            if (cnn == null || cnn.isNull() || !cnn.isObject()) {
                throw invalidResponse("AI analysis response is missing cnn_result", null);
            }
            return new AiPhotoAnalysisExchange(cnn, nullable(response.get("llm_result")),
                    nullable(response.get("survey")), body);
        } catch (RestClientResponseException exception) {
            throw responseFailure(exception);
        } catch (ResourceAccessException exception) {
            if (hasCause(exception, HttpTimeoutException.class)
                    || hasCause(exception, java.net.SocketTimeoutException.class)) {
                throw new AiAnalysisException("AI_ANALYSIS_TIMEOUT", "AI analysis timed out", exception);
            }
            throw new AiAnalysisException("AI_ANALYSIS_UNAVAILABLE", "AI analysis service is unavailable", exception);
        }
    }

    private JsonNode read(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (JacksonException exception) {
            throw invalidResponse("AI analysis response JSON is invalid", exception);
        }
    }

    private AiAnalysisException responseFailure(RestClientResponseException exception) {
        HttpStatusCode status = exception.getStatusCode();
        if (status.value() == 400) {
            return new AiAnalysisException("AI_ANALYSIS_INVALID_REQUEST", "AI rejected the analysis input", exception);
        }
        if (status.is5xxServerError()) {
            return new AiAnalysisException("AI_ANALYSIS_SERVER_ERROR", "AI analysis failed", exception);
        }
        return new AiAnalysisException("AI_ANALYSIS_HTTP_ERROR", "AI analysis returned an HTTP error", exception);
    }

    private AiAnalysisException invalidResponse(String message, Throwable cause) {
        return new AiAnalysisException("AI_ANALYSIS_INVALID_RESPONSE", message, cause);
    }

    private JsonNode nullable(JsonNode value) {
        return value == null || value.isNull() ? null : value;
    }

    private boolean hasCause(Throwable error, Class<? extends Throwable> type) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (type.isInstance(current)) {
                return true;
            }
        }
        return false;
    }

    private static final class NamedByteArrayResource extends ByteArrayResource {
        private final String filename;

        private NamedByteArrayResource(byte[] byteArray, String filename) {
            super(byteArray);
            this.filename = filename;
        }

        @Override
        public String getFilename() {
            return filename;
        }
    }
}
