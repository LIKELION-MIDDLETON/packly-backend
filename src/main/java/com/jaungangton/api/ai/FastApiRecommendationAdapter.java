package com.jaungangton.api.ai;

import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Component
public class FastApiRecommendationAdapter implements AiRecommendationPort {
    private static final Map<String, String> SUITABILITY_KEYS = suitabilityKeys();
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public FastApiRecommendationAdapter(
            ObjectMapper objectMapper,
            @Value("${centralton.ai.recommendation-base-url}") String baseUrl,
            @Value("${centralton.ai.recommendation-connect-timeout:2s}") Duration connectTimeout,
            @Value("${centralton.ai.recommendation-read-timeout:10s}") Duration readTimeout) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(connectTimeout)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
        this.objectMapper = objectMapper;
    }

    FastApiRecommendationAdapter(RestClient restClient, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public AiRecommendationExchange recommend(AiRecommendationRequest request) {
        RequestWire wire = new RequestWire(
                request.cnnResult(), request.llmResult(), surveyWire(request.survey()),
                request.budgetTotal(), request.alpha());
        String requestSnapshot = write(wire, "AI request could not be serialized");
        try {
            String responseSnapshot = restClient.post()
                    .uri("/recommend")
                    .body(wire)
                    .retrieve()
                    .body(String.class);
            if (responseSnapshot == null || responseSnapshot.isBlank()) {
                throw invalidResponse("AI returned an empty response", null);
            }
            ResponseWire response = read(responseSnapshot);
            AiRecommendationResult result = normalize(response);
            return new AiRecommendationExchange(result, requestSnapshot, responseSnapshot);
        } catch (RestClientResponseException exception) {
            throw responseFailure(exception);
        } catch (ResourceAccessException exception) {
            if (hasCause(exception, HttpTimeoutException.class)
                    || hasCause(exception, java.net.SocketTimeoutException.class)) {
                throw new AiRecommendationException("AI_TIMEOUT", "AI recommendation timed out", exception);
            }
            throw new AiRecommendationException("AI_UNAVAILABLE", "AI recommendation service is unavailable", exception);
        }
    }

    private ResponseWire read(String body) {
        try {
            return objectMapper.readValue(body, ResponseWire.class);
        } catch (JacksonException exception) {
            throw invalidResponse("AI response JSON does not match the recommendation contract", exception);
        }
    }

    private String write(Object value, String message) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new AiRecommendationException("AI_INVALID_REQUEST", message, exception);
        }
    }

    private AiRecommendationResult normalize(ResponseWire response) {
        if (response == null || blank(response.diagnosis()) || blank(response.headline())
                || blank(response.summary()) || blank(response.triage())
                || response.confidence() == null || response.confidence() < 0 || response.confidence() > 1
                || response.medicalRecommended() == null) {
            throw invalidResponse("AI response is missing required recommendation fields", null);
        }
        if (response.products() == null) {
            throw invalidResponse("AI response is missing the product collection", null);
        }
        List<ProductWire> products = response.products();
        if (products.size() > 8) {
            throw invalidResponse("AI response contains more than eight products", null);
        }
        Set<Integer> orders = new HashSet<>();
        List<AiRecommendationProduct> normalizedProducts = new ArrayList<>();
        for (ProductWire product : products) {
            if (product == null) {
                throw invalidResponse("AI response contains an invalid product", null);
            }
            Integer displayOrder = displayOrder(product);
            if (displayOrder == null || displayOrder < 1 || displayOrder > 8
                    || !orders.add(displayOrder)
                    || blank(product.slot()) || blank(product.goodsNo()) || blank(product.brand())
                    || blank(product.name())
                    || product.unscented() == null || product.comedogenicScore() == null
                    || product.comedogenicScore() < 0) {
                throw invalidResponse("AI response contains an invalid product", null);
            }
            Integer applicationOrder = ProductSlotNormalizer.applicationOrder(displayOrder);
            String usageGroup = ProductSlotNormalizer.usageGroup(displayOrder, product.slot());
            if (product.applicationOrder() != null && !Objects.equals(applicationOrder, product.applicationOrder())) {
                throw invalidResponse("AI response contains an invalid application order", null);
            }
            if (product.usageGroup() != null && !usageGroup.equals(product.usageGroup().trim().toUpperCase(Locale.ROOT))) {
                throw invalidResponse("AI response contains an invalid usage group", null);
            }
            if (product.dailyPrice() != null && product.dailyPrice() < 0
                    || product.salePrice() != null && product.salePrice() < 0
                    || product.legacyPrice() != null && product.legacyPrice() < 0) {
                throw invalidResponse("AI response contains a negative price", null);
            }
            if (product.salePrice() != null && product.legacyPrice() != null
                    && !product.salePrice().equals(product.legacyPrice())) {
                throw invalidResponse("AI response contains conflicting sale prices", null);
            }
            Long compatibilityPrice = product.salePrice() != null
                    ? product.salePrice()
                    : product.legacyPrice();
            normalizedProducts.add(new AiRecommendationProduct(
                    displayOrder, applicationOrder, usageGroup, product.slot(), product.goodsNo(), product.brand(),
                    product.name(), compatibilityPrice, normalizeSuitability(product.suitability()),
                    suitabilitySource(product.suitabilitySource()), product.functionalInfo(), product.unscented(),
                    product.comedogenicScore(), product.dailyPrice(), optionalText(product.dailyVolume()),
                    optionalText(product.totalVolume()), product.salePrice(),
                    optionalText(product.recommendationReason())));
        }
        normalizedProducts.sort(Comparator.comparingInt(AiRecommendationProduct::displayOrder));
        Long legacyTotalPrice = response.legacyTotalPrice();
        if (legacyTotalPrice != null && legacyTotalPrice < 0) {
            throw invalidResponse("AI response contains a negative total price", null);
        }
        if (legacyTotalPrice == null
                && normalizedProducts.stream().allMatch(product -> product.price() != null)) {
            legacyTotalPrice = normalizedProducts.stream()
                    .map(AiRecommendationProduct::price)
                    .reduce(0L, Math::addExact);
        }
        Long totalPriceDaily = response.totalPriceDaily();
        if (totalPriceDaily != null && totalPriceDaily < 0) {
            throw invalidResponse("AI response contains a negative daily total price", null);
        }
        if (legacyTotalPrice == null && totalPriceDaily == null) {
            throw invalidResponse("AI response is missing both legacy and daily total prices", null);
        }
        return new AiRecommendationResult(
                response.diagnosis(), response.headline(), response.summary(), response.confidence(),
                triage(response.triage()),
                response.medicalRecommended(), safe(response.medicalReasons()),
                normalizeReflectedSurvey(response.reflectedSurvey()),
                normalizedProducts, legacyTotalPrice, totalPriceDaily, response.analysisSummary(),
                safe(response.careRecommendations()), response.disclaimer());
    }

    private AiRecommendationException responseFailure(RestClientResponseException exception) {
        HttpStatusCode status = exception.getStatusCode();
        if (status.value() == 400) {
            return new AiRecommendationException("AI_INVALID_REQUEST", "AI rejected the analysis input", exception);
        }
        if (status.is5xxServerError()) {
            return new AiRecommendationException("AI_SERVER_ERROR", "AI recommendation failed", exception);
        }
        return new AiRecommendationException("AI_HTTP_ERROR", "AI recommendation returned HTTP " + status.value(), exception);
    }

    private AiRecommendationException invalidResponse(String message, Throwable cause) {
        return new AiRecommendationException("AI_INVALID_RESPONSE", message, cause);
    }

    private boolean hasCause(Throwable error, Class<? extends Throwable> type) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (type.isInstance(current)) {
                return true;
            }
        }
        return false;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private String optionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private Integer first(Integer... values) {
        for (Integer value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private Integer displayOrder(ProductWire product) {
        Integer explicit = first(product.displayOrder(), product.displayOrderCamel());
        if (product.displayOrder() != null && product.displayOrderCamel() != null
                && !product.displayOrder().equals(product.displayOrderCamel())) {
            throw invalidResponse("AI response contains conflicting display orders", null);
        }
        if (explicit != null && product.order() != null && !explicit.equals(product.order())) {
            throw invalidResponse("AI response contains a conflicting deprecated order alias", null);
        }
        return first(explicit, product.order());
    }

    private List<String> safe(List<String> values) {
        if (values == null) {
            return List.of();
        }
        if (values.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw invalidResponse("AI response contains an invalid text list", null);
        }
        return List.copyOf(values);
    }

    private String triage(String value) {
        return switch (value) {
            case "normal" -> "NORMAL";
            case "미용케어" -> "COSMETIC_CARE";
            case "의료필요" -> "MEDICAL_CARE_REQUIRED";
            default -> throw invalidResponse("AI response contains an unknown triage value", null);
        };
    }

    private String suitabilitySource(String value) {
        if (value == null) {
            return null;
        }
        return switch (value) {
            case "실측" -> "MEASURED";
            case "예측" -> "PREDICTED";
            default -> throw invalidResponse("AI response contains an unknown suitability source", null);
        };
    }

    private JsonNode normalizeSuitability(JsonNode value) {
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isObject()) {
            throw invalidResponse("AI product suitability must be an object", null);
        }
        ObjectNode normalized = objectMapper.createObjectNode();
        value.properties().forEach(entry -> {
            String key = SUITABILITY_KEYS.get(entry.getKey());
            if (key == null) {
                throw invalidResponse("AI product suitability contains an unknown key", null);
            }
            normalized.set(key, entry.getValue());
        });
        return normalized;
    }

    private JsonNode normalizeReflectedSurvey(JsonNode value) {
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isObject()) {
            throw invalidResponse("AI reflected survey must be an object", null);
        }
        ObjectNode normalized = objectMapper.createObjectNode();
        copy(value, normalized, "피부타입", "skinType");
        copy(value, normalized, "고민", "concerns");
        copy(value, normalized, "무향필터", "unscentedFilter");
        return normalized;
    }

    private void copy(JsonNode source, ObjectNode target, String wireName, String domainName) {
        JsonNode value = source.get(wireName);
        if (value != null) {
            target.set(domainName, value);
        }
    }

    private static Map<String, String> suitabilityKeys() {
        Map<String, String> keys = new LinkedHashMap<>();
        keys.put("건성적합", "drySkinFit");
        keys.put("지성적합", "oilySkinFit");
        keys.put("저자극", "lowIrritation");
        keys.put("진정효과", "soothingEffect");
        keys.put("보습효과", "moisturizingEffect");
        keys.put("미백효과", "brighteningEffect");
        return Map.copyOf(keys);
    }

    private SurveyWire surveyWire(JsonNode survey) {
        if (survey == null || survey.isNull()) {
            return null;
        }
        return new SurveyWire(
                integer(survey, "skinType", "skin_type"),
                node(survey, "concerns", "concerns"),
                integer(survey, "duration", "duration"),
                node(survey, "areas", "areas"),
                integer(survey, "irritation", "irritation"),
                integer(survey, "diagnosed", "diagnosed"));
    }

    private Integer integer(JsonNode parent, String camelName, String snakeName) {
        JsonNode value = node(parent, camelName, snakeName);
        return value == null || value.isNull() ? null : value.asInt();
    }

    private JsonNode node(JsonNode parent, String camelName, String snakeName) {
        JsonNode value = parent.get(camelName);
        return value == null ? parent.get(snakeName) : value;
    }

    private record RequestWire(
            @JsonProperty("cnn_result") JsonNode cnnResult,
            @JsonProperty("llm_result") JsonNode llmResult,
            @JsonProperty("survey") SurveyWire survey,
            @JsonProperty("budget_total") Long budgetTotal,
            @JsonProperty("alpha") double alpha) {
    }

    private record SurveyWire(
            @JsonProperty("skin_type") Integer skinType,
            @JsonProperty("concerns") JsonNode concerns,
            @JsonProperty("duration") Integer duration,
            @JsonProperty("areas") JsonNode areas,
            @JsonProperty("irritation") Integer irritation,
            @JsonProperty("diagnosed") Integer diagnosed) {
    }

    private record ResponseWire(
            @JsonProperty("진단") String diagnosis,
            @JsonProperty("헤드라인") String headline,
            @JsonProperty("요약") String summary,
            @JsonProperty("신뢰도") Double confidence,
            @JsonProperty("트리아지") String triage,
            @JsonProperty("의료상담권고") Boolean medicalRecommended,
            @JsonProperty("의료권고사유") List<String> medicalReasons,
            @JsonProperty("반영된설문") JsonNode reflectedSurvey,
            @JsonProperty("구성") List<ProductWire> products,
            @JsonProperty("총액") Long legacyTotalPrice,
            @JsonProperty("총액_일일") Long totalPriceDaily,
            @JsonProperty("분석요약") String analysisSummary,
            @JsonProperty("생활수칙") List<String> careRecommendations,
            @JsonProperty("고지사항") String disclaimer) {
    }

    private record ProductWire(
            @JsonProperty("순서") Integer order,
            @JsonProperty("display_order") Integer displayOrder,
            @JsonProperty("displayOrder") Integer displayOrderCamel,
            @JsonProperty("application_order") Integer applicationOrder,
            @JsonProperty("applicationOrder") Integer applicationOrderCamel,
            @JsonProperty("usage_group") String usageGroup,
            @JsonProperty("usageGroup") String usageGroupCamel,
            @JsonProperty("슬롯") String slot,
            @JsonProperty("goods_no") String goodsNo,
            @JsonProperty("brand") String brand,
            @JsonProperty("name") String name,
            @JsonProperty("가격") Long legacyPrice,
            @JsonProperty("일일가격") Long dailyPrice,
            @JsonProperty("일일용량") String dailyVolume,
            @JsonProperty("전체용량") String totalVolume,
            @JsonProperty("판매가") Long salePrice,
            @JsonProperty("추천이유") String recommendationReason,
            @JsonProperty("적합도") JsonNode suitability,
            @JsonProperty("적합도출처") String suitabilitySource,
            @JsonProperty("고시") String functionalInfo,
            @JsonProperty("무향") Boolean unscented,
            @JsonProperty("코메도") Integer comedogenicScore) {

        public Integer applicationOrder() {
            return applicationOrder != null ? applicationOrder : applicationOrderCamel;
        }

        public String usageGroup() {
            return usageGroup != null ? usageGroup : usageGroupCamel;
        }
    }
}
