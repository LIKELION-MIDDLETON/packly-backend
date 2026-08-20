package com.jaungangton.api.auth;

import java.time.Clock;
import java.util.UUID;

import com.jaungangton.api.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserOnboardingService {
    private final UserRepository users;
    private final Clock clock;

    public UserOnboardingService(UserRepository users, Clock clock) {
        this.users = users;
        this.clock = clock;
    }

    @Transactional
    public void advance(UUID userId, OnboardingStatus status) {
        User user = users.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND",
                        "사용자를 찾을 수 없습니다."));
        try {
            user.advanceOnboarding(status, clock.instant());
        } catch (IllegalStateException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_ONBOARDING_TRANSITION",
                    "현재 온보딩 상태에서는 요청한 단계로 진행할 수 없습니다.");
        }
    }

    @Transactional(readOnly = true)
    public void requireSurveyEntry(UUID userId) {
        User user = users.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND",
                        "사용자를 찾을 수 없습니다."));
        if (user.getOnboardingStatus() == OnboardingStatus.PROFILE_REQUIRED) {
            throw new ApiException(HttpStatus.CONFLICT, "PROFILE_REQUIRED",
                    "설문 전에 프로필을 완료해 주세요.");
        }
    }
}
