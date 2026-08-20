package com.jaungangton.api.analysis;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.jaungangton.api.ai.AiAnalysisException;
import com.jaungangton.api.ai.AiPhotoAnalysisExchange;
import com.jaungangton.api.ai.AiRecommendationException;
import com.jaungangton.api.ai.SurveyAnalysisAnswersAdapter;
import com.jaungangton.api.common.ApiException;
import com.jaungangton.api.recommendation.RecommendationFailureService;
import com.jaungangton.api.recommendation.RecommendationWorkflowService;
import com.jaungangton.api.survey.SurveyNumericSnapshot;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class PhotoAnalysisWorkflowService {
    static final int MAX_PHOTO_BYTES = 10 * 1024 * 1024;
    static final int ANALYZE_TOP_K = 8;
    private static final String PHOTO_SOURCE_PREFIX = "photo-analysis-";
    private static final List<String> ALLOWED_CONTENT_TYPES = List.of("image/jpeg", "image/png", "image/webp");

    private final AnalysisService analysisService;
    private final PhotoAnalysisPort photoAnalysisPort;
    private final SurveyAnalysisAnswersAdapter answersAdapter;
    private final AnalysisResultAcceptanceService acceptanceService;
    private final RecommendationWorkflowService recommendationWorkflow;
    private final RecommendationFailureService failureService;
    private final ObjectMapper objectMapper;
    private final Executor executor;

    public PhotoAnalysisWorkflowService(
            AnalysisService analysisService,
            PhotoAnalysisPort photoAnalysisPort,
            SurveyAnalysisAnswersAdapter answersAdapter,
            AnalysisResultAcceptanceService acceptanceService,
            RecommendationWorkflowService recommendationWorkflow,
            RecommendationFailureService failureService,
            ObjectMapper objectMapper,
            @Qualifier("photoAnalysisExecutor") Executor executor) {
        this.analysisService = analysisService;
        this.photoAnalysisPort = photoAnalysisPort;
        this.answersAdapter = answersAdapter;
        this.acceptanceService = acceptanceService;
        this.recommendationWorkflow = recommendationWorkflow;
        this.failureService = failureService;
        this.objectMapper = objectMapper;
        this.executor = executor;
    }

    public AnalysisResponse uploadAndStart(UUID userId, UUID analysisId, MultipartFile image) {
        validate(image);
        byte[] photo;
        try {
            photo = image.getBytes();
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PHOTO", "사진을 읽을 수 없습니다.");
        }
        if (photo.length == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EMPTY_PHOTO", "빈 사진은 업로드할 수 없습니다.");
        }
        if (photo.length > MAX_PHOTO_BYTES) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "PHOTO_TOO_LARGE", "사진은 10MB 이하여야 합니다.");
        }
        String contentType = normalizedContentType(image.getContentType());
        if (!matchesMagicBytes(photo, contentType)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PHOTO_CONTENT",
                    "사진 내용이 MIME 형식과 일치하지 않습니다.");
        }

        PhotoUploadResult upload = analysisService.attachPhoto(userId, analysisId, photo, contentType);
        if (upload.started()) {
            try {
                executor.execute(() -> process(analysisId));
            } catch (RuntimeException exception) {
                failSafely(analysisId, "AI_ANALYSIS_UNAVAILABLE");
            }
        }
        return upload.response();
    }

    void process(UUID analysisId) {
        try {
            PhotoAnalysisInput input = analysisService.photoInput(analysisId);
            SurveyNumericSnapshot snapshot = readSnapshot(input.surveySnapshot());
            String answersJson = answersAdapter.toJson(snapshot);
            AiPhotoAnalysisExchange exchange = photoAnalysisPort.analyze(
                    input.photoData(), input.contentType(), answersJson, ANALYZE_TOP_K);
            RecommendationWork work = acceptanceService.acceptPhotoAnalysisResult(
                    analysisId,
                    new PhotoAnalysisResult(
                            PHOTO_SOURCE_PREFIX + analysisId,
                            exchange.cnnResult(), exchange.llmResult(), exchange.survey()));
            recommendationWorkflow.process(work);
        } catch (AiAnalysisException exception) {
            failSafely(analysisId, exception.failureCode());
        } catch (AiRecommendationException exception) {
            // RecommendationWorkflowService has already persisted the stable failureCode.
        } catch (PhotoStorageException exception) {
            failSafely(analysisId, "PHOTO_STORAGE_UNAVAILABLE");
        } catch (JacksonException exception) {
            failSafely(analysisId, "AI_ANALYSIS_INVALID_REQUEST");
        } catch (RuntimeException exception) {
            failSafely(analysisId, "AI_ANALYSIS_FAILED");
        }
    }

    private SurveyNumericSnapshot readSnapshot(String value) throws JacksonException {
        JsonNode root = objectMapper.readTree(value);
        return new SurveyNumericSnapshot(
                root.path("skinType").asInt(),
                integerList(root.path("concerns")),
                root.path("duration").asInt(),
                integerList(root.path("areas")),
                root.path("irritation").asInt(),
                root.path("diagnosed").asInt());
    }

    private List<Integer> integerList(JsonNode node) {
        if (!node.isArray()) {
            throw new AiAnalysisException("AI_ANALYSIS_INVALID_REQUEST", "Stored survey contains an invalid list");
        }
        return java.util.stream.StreamSupport.stream(node.spliterator(), false)
                .map(JsonNode::asInt)
                .toList();
    }

    private void validate(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EMPTY_PHOTO", "빈 사진은 업로드할 수 없습니다.");
        }
        String contentType = normalizedContentType(image.getContentType());
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_PHOTO_TYPE",
                    "JPEG, PNG, WEBP 사진만 업로드할 수 있습니다.");
        }
        if (image.getSize() > MAX_PHOTO_BYTES) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "PHOTO_TOO_LARGE", "사진은 10MB 이하여야 합니다.");
        }
    }

    private String normalizedContentType(String contentType) {
        if (contentType == null) {
            return null;
        }
        String normalized = contentType.toLowerCase(java.util.Locale.ROOT);
        return "image/jpg".equals(normalized) ? "image/jpeg" : normalized;
    }

    private void failSafely(UUID analysisId, String failureCode) {
        try {
            failureService.fail(analysisId, failureCode);
        } catch (RuntimeException ignored) {
            // A concurrent retry or callback may have moved the job already. Do not expose it to the worker.
        }
    }

    private boolean matchesMagicBytes(byte[] photo, String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> photo.length >= 3
                    && (photo[0] & 0xff) == 0xff && (photo[1] & 0xff) == 0xd8 && (photo[2] & 0xff) == 0xff;
            case "image/png" -> photo.length >= 8
                    && (photo[0] & 0xff) == 0x89 && photo[1] == 0x50 && photo[2] == 0x4e && photo[3] == 0x47
                    && photo[4] == 0x0d && photo[5] == 0x0a && photo[6] == 0x1a && photo[7] == 0x0a;
            case "image/webp" -> photo.length >= 12
                    && photo[0] == 'R' && photo[1] == 'I' && photo[2] == 'F' && photo[3] == 'F'
                    && photo[8] == 'W' && photo[9] == 'E' && photo[10] == 'B' && photo[11] == 'P';
            default -> false;
        };
    }
}
