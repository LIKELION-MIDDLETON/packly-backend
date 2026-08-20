package com.jaungangton.api.auth;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ProfileRequestValidationTest {
    private static jakarta.validation.ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @Test
    void validatesKoreanPostalCodeAndTrimmedNicknameLength() {
        ProfileRequest request = new ProfileRequest(" a ", "1234", "서울", null);

        assertThat(validator.validate(request)).extracting(v -> v.getPropertyPath().toString())
                .contains("postalCode", "nicknameLengthValid");
    }
}
