package com.jaungangton.api.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URI;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class KakaoProductImageProviderTest {
    @Test
    void returnsTheFirstSecureThumbnailAndSendsTheRestApiKey() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://dapi.kakao.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KakaoProductImageProvider provider = new KakaoProductImageProvider(builder.build(), "test-rest-key");

        server.expect(request -> {
                    URI uri = request.getURI();
                    assertThat(uri.getPath()).isEqualTo("/v2/search/image");
                    assertThat(uri.getQuery()).contains("sort=accuracy", "size=5");
                    assertThat(uri.getQuery()).contains("Brand");
                    assertThat(request.getHeaders().getFirst("Authorization"))
                            .isEqualTo("KakaoAK test-rest-key");
                })
                .andRespond(withSuccess("""
                        {
                          "documents": [
                            {
                              "thumbnail_url": "https://search.kakaocdn.test/product.jpg",
                              "image_url": "http://source.example.test/product.jpg"
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        Optional<String> result = provider.findImageUrl("Brand", "Product Name");

        assertThat(result).contains("https://search.kakaocdn.test/product.jpg");
        server.verify();
    }

    @Test
    void doesNotCallTheApiWhenTheKeyIsMissing() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://dapi.kakao.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KakaoProductImageProvider provider = new KakaoProductImageProvider(builder.build(), " ");

        assertThat(provider.findImageUrl("Brand", "Product Name")).isEmpty();
        server.verify();
    }

    @Test
    void keepsRecommendationGenerationAvailableWhenTheApiFails() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://dapi.kakao.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KakaoProductImageProvider provider = new KakaoProductImageProvider(builder.build(), "test-rest-key");
        server.expect(request -> assertThat(request.getURI().getPath()).isEqualTo("/v2/search/image"))
                .andRespond(withServerError());

        assertThat(provider.findImageUrl("Brand", "Product Name")).isEmpty();
        server.verify();
    }
}
