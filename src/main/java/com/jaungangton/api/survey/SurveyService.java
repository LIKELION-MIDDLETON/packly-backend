package com.jaungangton.api.survey;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jaungangton.api.common.ApiException;
import com.jaungangton.api.auth.OnboardingStatus;
import com.jaungangton.api.auth.UserOnboardingService;

@Service
public class SurveyService {
    private final SurveyRepository repository;
    private final SurveyMapper mapper;
    private final Clock clock;
    private final UserOnboardingService onboarding;

    @Autowired
    public SurveyService(SurveyRepository repository, SurveyMapper mapper, UserOnboardingService onboarding) {
        this(repository, mapper, onboarding, Clock.systemUTC());
    }

    SurveyService(SurveyRepository repository, SurveyMapper mapper, UserOnboardingService onboarding, Clock clock) {
        this.repository = repository;
        this.mapper = mapper;
        this.onboarding = onboarding;
        this.clock = clock;
    }

    SurveyService(SurveyRepository repository, SurveyMapper mapper, Clock clock) {
        this(repository, mapper, null, clock);
    }

    @Transactional
    public SurveyResponse upsert(UUID userId, SurveyRequest request) {
        if (onboarding != null) onboarding.requireSurveyEntry(userId);
        Survey survey = repository.findByUserId(userId)
                .orElseGet(() -> new Survey(UUID.randomUUID(), userId));
        survey.update(request, mapper, Instant.now(clock));
        SurveyResponse response = mapper.toResponse(repository.save(survey));
        if (onboarding != null) onboarding.advance(userId, OnboardingStatus.PHOTO_REQUIRED);
        return response;
    }

    @Transactional(readOnly = true)
    public SurveyResponse get(UUID userId) {
        return repository.findByUserId(userId)
                .map(mapper::toResponse)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SURVEY_NOT_FOUND", "저장된 설문이 없습니다."));
    }

    @Transactional(readOnly = true)
    public Survey requireEntity(UUID userId) {
        return repository.findByUserId(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "SURVEY_REQUIRED", "분석 전에 설문을 완료해 주세요."));
    }

    public SurveyNumericSnapshot numericSnapshot(Survey survey) {
        return mapper.toResponse(survey).aiNumericSnapshot();
    }
}
