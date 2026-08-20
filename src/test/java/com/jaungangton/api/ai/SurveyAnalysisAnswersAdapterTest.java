package com.jaungangton.api.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.jaungangton.api.survey.SurveyNumericSnapshot;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class SurveyAnalysisAnswersAdapterTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SurveyAnalysisAnswersAdapter adapter = new SurveyAnalysisAnswersAdapter(objectMapper);

    @Test
    void mapsEveryPersistedEnumCodeToTheExactAnalyzeLabels() {
        for (int code = 1; code <= 6; code++) {
            JsonNode answers = json(new SurveyNumericSnapshot(code, List.of(1), 1, List.of(1), 1, 1));
            assertThat(answers.get("skin_type").asText()).isEqualTo(List.of(
                    "건성", "지성", "복합성(T존 지성/볼 건성)", "수부지(속건조 지성)", "민감성", "잘 모르겠음").get(code - 1));
        }
        for (int code = 1; code <= 8; code++) {
            JsonNode answers = json(new SurveyNumericSnapshot(1, List.of(code), 1, List.of(1), 1, 1));
            assertThat(answers.get("main_concern").get(0).asText()).isEqualTo(List.of(
                    "여드름/뾰루지", "블랙헤드/모공", "홍조/붉은기", "건조함/각질", "색소침착/기미",
                    "주름/탄력저하", "가려움/따가움", "특별한 고민 없음").get(code - 1));
        }
        for (int code = 1; code <= 5; code++) {
            JsonNode answers = json(new SurveyNumericSnapshot(1, List.of(1), code, List.of(1), 1, 1));
            assertThat(answers.get("duration").asText()).isEqualTo(List.of(
                    "해당 없음", "1주 이내", "1~4주", "1~3개월", "3개월 이상").get(code - 1));
        }
        for (int code = 1; code <= 7; code++) {
            JsonNode answers = json(new SurveyNumericSnapshot(1, List.of(1), 1, List.of(code), 1, 1));
            assertThat(answers.get("location").get(0).asText()).isEqualTo(List.of(
                    "이마", "코(T존)", "볼", "턱", "눈가", "얼굴 전체", "해당 없음").get(code - 1));
        }
        for (int code = 1; code <= 3; code++) {
            JsonNode answers = json(new SurveyNumericSnapshot(1, List.of(1), 1, List.of(1), code, 1));
            assertThat(answers.get("sensitivity").asText()).isEqualTo(List.of(
                    "전혀 그렇지 않다", "가끔 그렇다", "자주 그렇다").get(code - 1));
        }
        for (int code = 1; code <= 6; code++) {
            JsonNode answers = json(new SurveyNumericSnapshot(1, List.of(1), 1, List.of(1), 1, code));
            assertThat(answers.get("history").asText()).isEqualTo(List.of(
                    "없음", "아토피피부염", "여드름(중증)", "지루성피부염", "건선", "기타(직접 입력)").get(code - 1));
        }
    }

    @Test
    void emitsTheSixAnalyzeKeysAndKeepsMultiSelectOrder() {
        JsonNode answers = json(new SurveyNumericSnapshot(2, List.of(1, 4, 8), 5, List.of(2, 6), 3, 6));

        assertThat(answers.has("skin_type")).isTrue();
        assertThat(answers.has("main_concern")).isTrue();
        assertThat(answers.has("duration")).isTrue();
        assertThat(answers.has("location")).isTrue();
        assertThat(answers.has("sensitivity")).isTrue();
        assertThat(answers.has("history")).isTrue();
        assertThat(answers.get("main_concern")).extracting(JsonNode::asText)
                .containsExactly("여드름/뾰루지", "건조함/각질", "특별한 고민 없음");
        assertThat(answers.get("location")).extracting(JsonNode::asText)
                .containsExactly("코(T존)", "얼굴 전체");
    }

    private JsonNode json(SurveyNumericSnapshot snapshot) {
        return objectMapper.readTree(adapter.toJson(snapshot));
    }
}
