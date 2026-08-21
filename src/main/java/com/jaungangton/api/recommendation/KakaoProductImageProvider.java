package com.jaungangton.api.recommendation;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class KakaoProductImageProvider implements ProductImageProvider {
    private static final Logger log = LoggerFactory.getLogger(KakaoProductImageProvider.class);

    private final RestClient restClient;
    private final String restApiKey;

    @Autowired
    public KakaoProductImageProvider(
            @Value("${centralton.product-image.kakao-base-url:https://dapi.kakao.com}") String baseUrl,
            @Value("${centralton.product-image.kakao-rest-api-key:}") String restApiKey,
            @Value("${centralton.product-image.connect-timeout:1s}") Duration connectTimeout,
            @Value("${centralton.product-image.read-timeout:2s}") Duration readTimeout) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(connectTimeout)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
        this.restApiKey = restApiKey == null ? "" : restApiKey.trim();
    }

    KakaoProductImageProvider(RestClient restClient, String restApiKey) {
        this.restClient = restClient;
        this.restApiKey = restApiKey == null ? "" : restApiKey.trim();
    }

    @Override
    public Optional<String> findImageUrl(String brand, String name) {
        if (restApiKey.isBlank() || blank(brand) || blank(name)) {
            return Optional.empty();
        }
        try {
            ImageSearchResponse response = restClient.get()
                    .uri(uri -> uri.path("/v2/search/image")
                            .queryParam("query", brand.trim() + " " + name.trim() + " 화장품")
                            .queryParam("sort", "accuracy")
                            .queryParam("size", 5)
                            .build())
                    .header(HttpHeaders.AUTHORIZATION, "KakaoAK " + restApiKey)
                    .retrieve()
                    .body(ImageSearchResponse.class);
            if (response == null || response.documents() == null) {
                return Optional.empty();
            }
            return response.documents().stream()
                    .map(document -> firstHttps(document.thumbnailUrl(), document.imageUrl()))
                    .flatMap(Optional::stream)
                    .findFirst();
        } catch (RestClientException exception) {
            log.warn("Product image lookup failed for brand={} name={}", brand, name);
            return Optional.empty();
        }
    }

    private Optional<String> firstHttps(String... candidates) {
        for (String candidate : candidates) {
            if (candidate == null || candidate.isBlank()) {
                continue;
            }
            try {
                URI uri = URI.create(candidate.trim());
                if ("https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null) {
                    return Optional.of(uri.toString());
                }
            } catch (IllegalArgumentException ignored) {
                // Ignore malformed search results and continue to the next candidate.
            }
        }
        return Optional.empty();
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    record ImageSearchResponse(List<ImageDocument> documents) {
    }

    record ImageDocument(
            @JsonProperty("thumbnail_url") String thumbnailUrl,
            @JsonProperty("image_url") String imageUrl) {
    }
}
