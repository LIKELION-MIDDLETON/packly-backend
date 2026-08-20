package com.jaungangton.api.auth;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ProfileRequest(
        @NotBlank String nickname,
        @NotBlank @Pattern(regexp = "\\d{5}") String postalCode,
        @NotBlank @Size(max = 200) String addressLine1,
        @Size(max = 200) String addressLine2) {

    @AssertTrue(message = "nickname must be 2 to 20 characters after trimming")
    public boolean isNicknameLengthValid() {
        if (nickname == null) return true;
        int length = nickname.strip().codePointCount(0, nickname.strip().length());
        return length >= 2 && length <= 20;
    }
}
