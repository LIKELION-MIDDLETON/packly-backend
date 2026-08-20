package com.jaungangton.api.common;

import java.util.LinkedHashMap;
import java.util.Map;

import com.jaungangton.api.ai.AiRecommendationException;
import com.jaungangton.api.ai.AiAnalysisException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.MediaType;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiError> handleApiException(ApiException exception, HttpServletRequest request) {
        return response(exception.status(),
                error(exception.status(), exception.code(), exception.getMessage(), request, Map.of()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request) {
        Map<String, String> fields = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                fields.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return response(HttpStatus.BAD_REQUEST,
                error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                        "요청 값을 확인해 주세요.", request, fields));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingRequestHeaderException.class})
    ResponseEntity<ApiError> handleBadRequest(Exception exception, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST,
                error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST",
                        "요청 형식을 확인해 주세요.", request, Map.of()));
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    ResponseEntity<ApiError> handleMissingMultipartPart(
            MissingServletRequestPartException exception, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST,
                error(HttpStatus.BAD_REQUEST, "PHOTO_REQUIRED", "image 파일 파트가 필요합니다.", request, Map.of()));
    }

    @ExceptionHandler({MultipartException.class, HttpMediaTypeNotSupportedException.class})
    ResponseEntity<ApiError> handleMalformedMultipart(Exception exception, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST,
                error(HttpStatus.BAD_REQUEST, "INVALID_MULTIPART",
                        "multipart/form-data 요청 형식을 확인해 주세요.", request, Map.of()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ApiError> handleUploadTooLarge(
            MaxUploadSizeExceededException exception, HttpServletRequest request) {
        return response(HttpStatus.PAYLOAD_TOO_LARGE,
                error(HttpStatus.PAYLOAD_TOO_LARGE, "PHOTO_TOO_LARGE", "사진은 10MB 이하여야 합니다.", request, Map.of()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiError> handleNotFound(NoResourceFoundException exception, HttpServletRequest request) {
        return response(HttpStatus.NOT_FOUND,
                error(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND",
                        "요청한 경로를 찾을 수 없습니다.", request, Map.of()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> handleConflict(DataIntegrityViolationException exception, HttpServletRequest request) {
        return response(HttpStatus.CONFLICT,
                error(HttpStatus.CONFLICT, "RESOURCE_CONFLICT",
                        "이미 처리된 요청입니다.", request, Map.of()));
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<ApiError> handleConcurrentUpdate(
            OptimisticLockingFailureException exception, HttpServletRequest request) {
        return response(HttpStatus.CONFLICT,
                error(HttpStatus.CONFLICT, "RESOURCE_CONFLICT",
                        "이미 처리된 요청입니다.", request, Map.of()));
    }

    @ExceptionHandler(AiRecommendationException.class)
    ResponseEntity<ApiError> handleAiFailure(AiRecommendationException exception, HttpServletRequest request) {
        boolean unavailable = switch (exception.failureCode()) {
            case "AI_TIMEOUT", "AI_UNAVAILABLE", "AI_SERVER_ERROR" -> true;
            default -> false;
        };
        HttpStatus status = unavailable ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.BAD_GATEWAY;
        return response(status,
                error(status, exception.failureCode(),
                        unavailable ? "AI 추천 서비스에 일시적으로 연결할 수 없습니다."
                                : "AI 추천 응답을 처리할 수 없습니다.",
                        request, Map.of()));
    }

    @ExceptionHandler(AiAnalysisException.class)
    ResponseEntity<ApiError> handleAiAnalysisFailure(AiAnalysisException exception, HttpServletRequest request) {
        boolean unavailable = switch (exception.failureCode()) {
            case "AI_ANALYSIS_TIMEOUT", "AI_ANALYSIS_UNAVAILABLE", "AI_ANALYSIS_SERVER_ERROR" -> true;
            default -> false;
        };
        HttpStatus status = unavailable ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.BAD_GATEWAY;
        return response(status,
                error(status, exception.failureCode(),
                        unavailable ? "AI 피부 분석 서비스에 일시적으로 연결할 수 없습니다."
                                : "AI 피부 분석 응답을 처리할 수 없습니다.",
                        request, Map.of()));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleUnexpected(Exception exception, HttpServletRequest request) {
        return response(HttpStatus.INTERNAL_SERVER_ERROR,
                error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                        "서버에서 요청을 처리하지 못했습니다.", request, Map.of()));
    }

    private ApiError error(
            HttpStatus status,
            String code,
            String detail,
            HttpServletRequest request,
            Map<String, String> fieldErrors) {
        return ApiError.of(status, code, detail, request.getRequestURI(), fieldErrors);
    }

    private ResponseEntity<ApiError> response(HttpStatus status, ApiError error) {
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(error);
    }
}
