package com.jaungangton.api.survey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

class SurveyServiceTest {

    @Test
    void upsertReusesTheUsersSingleSurvey() {
        SurveyRepository repository = mock(SurveyRepository.class);
        SurveyMapper mapper = new SurveyMapper();
        UUID userId = UUID.randomUUID();
        AtomicReference<Survey> stored = new AtomicReference<>();
        when(repository.findByUserId(userId)).thenAnswer(invocation -> Optional.ofNullable(stored.get()));
        when(repository.save(any(Survey.class))).thenAnswer(invocation -> {
            Survey survey = invocation.getArgument(0);
            stored.set(survey);
            return survey;
        });
        SurveyService service = new SurveyService(
                repository, mapper, Clock.fixed(Instant.parse("2026-08-19T00:00:00Z"), ZoneOffset.UTC));

        SurveyResponse first = service.upsert(userId, request(SkinType.DRY));
        UUID firstId = stored.get().id();
        SurveyResponse second = service.upsert(userId, request(SkinType.OILY));

        assertThat(stored.get().id()).isEqualTo(firstId);
        assertThat(first.skinType()).isEqualTo(SkinType.DRY);
        assertThat(second.skinType()).isEqualTo(SkinType.OILY);
        assertThat(second.aiNumericSnapshot().skinType()).isEqualTo(2);
    }

    private SurveyRequest request(SkinType skinType) {
        return new SurveyRequest(
                skinType,
                List.of(SkinConcern.NONE),
                ConcernDuration.NOT_APPLICABLE,
                List.of(FaceArea.NONE),
                IrritationFrequency.NEVER,
                DiagnosedCondition.NONE,
                null);
    }
}
