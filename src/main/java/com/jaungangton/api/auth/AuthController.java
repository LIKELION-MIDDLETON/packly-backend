package com.jaungangton.api.auth;

import java.time.Instant;
import java.util.UUID;

import com.jaungangton.api.common.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class AuthController {
    private final AuthService authService;
    private final ProfileService profileService;

    public AuthController(AuthService authService, ProfileService profileService) {
        this.authService = authService;
        this.profileService = profileService;
    }

    @PostMapping("/auth/google")
    AuthResponse google(@Valid @RequestBody GoogleAuthRequest request) {
        return AuthResponse.from(authService.exchangeGoogle(request.idToken(), request.termsAccepted()));
    }

    @PostMapping("/auth/refresh")
    AuthResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return AuthResponse.from(authService.refresh(request.refreshToken()));
    }

    @PostMapping("/auth/logout")
    ResponseEntity<Void> logout(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody RefreshRequest request) {
        authService.logout(currentUserId(jwt), request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return UserResponse.from(authService.me(currentUserId(jwt)));
    }

    @PutMapping("/me/profile")
    UserResponse profile(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ProfileRequest request) {
        return UserResponse.from(profileService.update(currentUserId(jwt), request));
    }

    private UUID currentUserId(Jwt jwt) {
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (RuntimeException exception) {
            throw new ApiException(org.springframework.http.HttpStatus.UNAUTHORIZED,
                    "INVALID_ACCESS_TOKEN", "Invalid access token subject.");
        }
    }

    public record GoogleAuthRequest(@NotBlank String idToken, Boolean termsAccepted) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    public record AuthResponse(String tokenType, String accessToken, Instant accessTokenExpiresAt,
                               String refreshToken, Instant refreshTokenExpiresAt, boolean isNewUser,
                               UserResponse user, OnboardingStatus onboardingStatus) {
        static AuthResponse from(AuthPersistenceService.AuthResult result) {
            return new AuthResponse("Bearer", result.accessToken(), result.accessTokenExpiresAt(),
                    result.refreshToken(), result.refreshTokenExpiresAt(), result.isNewUser(),
                    UserResponse.from(result.user()), result.user().getOnboardingStatus());
        }
    }

    public record UserResponse(UUID id, String email, String displayName, String avatarUrl,
                               String nickname, String postalCode, String addressLine1,
                               String addressLine2, OnboardingStatus onboardingStatus) {
        static UserResponse from(User user) {
            return new UserResponse(user.getId(), user.getEmail(), user.getName(), user.getAvatarUrl(),
                    user.getNickname(), user.getPostalCode(), user.getAddressLine1(), user.getAddressLine2(),
                    user.getOnboardingStatus());
        }
    }
}
