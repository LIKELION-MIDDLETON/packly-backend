package com.jaungangton.api.auth;

import java.time.Clock;
import java.util.UUID;

import com.jaungangton.api.common.ApiException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileService {
    private final UserRepository users;
    private final Clock clock;

    public ProfileService(UserRepository users, Clock clock) {
        this.users = users;
        this.clock = clock;
    }

    @Transactional
    public User update(UUID userId, ProfileRequest request) {
        User user = users.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND",
                        "사용자를 찾을 수 없습니다."));
        String normalizedNickname = User.normalizeNickname(request.nickname());
        if (users.existsByNormalizedNicknameAndIdNot(normalizedNickname, userId)) {
            throw nicknameConflict();
        }
        user.updateProfile(request.nickname(), request.postalCode(), request.addressLine1(),
                request.addressLine2(), clock.instant());
        try {
            return users.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw nicknameConflict();
        }
    }

    private ApiException nicknameConflict() {
        return new ApiException(HttpStatus.CONFLICT, "NICKNAME_ALREADY_IN_USE",
                "이미 사용 중인 닉네임입니다.");
    }
}
