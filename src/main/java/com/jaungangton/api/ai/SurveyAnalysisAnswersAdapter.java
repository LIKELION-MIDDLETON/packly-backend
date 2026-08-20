package com.jaungangton.api.ai;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.jaungangton.api.survey.SurveyNumericSnapshot;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** Converts the persisted numeric survey snapshot to the /analyze Korean-label contract. */
@Component
public class SurveyAnalysisAnswersAdapter {
    private static final Map<Integer, String> SKIN_TYPES = Map.of(
            1, "건성", 2, "지성", 3, "복합성(T존 지성/볼 건성)",
            4, "수부지(속건조 지성)", 5, "민감성", 6, "잘 모르겠음");
    private static final Map<Integer, String> CONCERNS = Map.of(
            1, "여드름/뾰루지", 2, "블랙헤드/모공", 3, "홍조/붉은기", 4, "건조함/각질",
            5, "색소침착/기미", 6, "주름/탄력저하", 7, "가려움/따가움", 8, "특별한 고민 없음");
    private static final Map<Integer, String> DURATIONS = Map.of(
            1, "해당 없음", 2, "1주 이내", 3, "1~4주", 4, "1~3개월", 5, "3개월 이상");
    private static final Map<Integer, String> AREAS = Map.of(
            1, "이마", 2, "코(T존)", 3, "볼", 4, "턱", 5, "눈가", 6, "얼굴 전체", 7, "해당 없음");
    private static final Map<Integer, String> IRRITATION = Map.of(
            1, "전혀 그렇지 않다", 2, "가끔 그렇다", 3, "자주 그렇다");
    private static final Map<Integer, String> DIAGNOSED = Map.of(
            1, "없음", 2, "아토피피부염", 3, "여드름(중증)",
            4, "지루성피부염", 5, "건선", 6, "기타(직접 입력)");

    private final ObjectMapper objectMapper;

    public SurveyAnalysisAnswersAdapter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String toJson(SurveyNumericSnapshot snapshot) {
        ObjectNode answers = objectMapper.createObjectNode();
        answers.put("skin_type", label(SKIN_TYPES, snapshot.skinType()));
        putArray(answers, "main_concern", snapshot.concerns(), CONCERNS);
        answers.put("duration", label(DURATIONS, snapshot.duration()));
        putArray(answers, "location", snapshot.areas(), AREAS);
        answers.put("sensitivity", label(IRRITATION, snapshot.irritation()));
        answers.put("history", label(DIAGNOSED, snapshot.diagnosed()));
        try {
            return objectMapper.writeValueAsString(answers);
        } catch (JacksonException exception) {
            throw new AiAnalysisException("AI_ANALYSIS_INVALID_REQUEST",
                    "AI analysis request could not be serialized", exception);
        }
    }

    private void putArray(ObjectNode parent, String key, List<Integer> codes, Map<Integer, String> labels) {
        ArrayNode values = parent.putArray(key);
        for (Integer code : codes) {
            values.add(label(labels, code));
        }
    }

    private String label(Map<Integer, String> labels, int code) {
        String value = labels.get(code);
        if (value == null) {
            throw new AiAnalysisException("AI_ANALYSIS_INVALID_REQUEST", "Stored survey contains an unknown value");
        }
        return value;
    }
}
