package com.jaungangton.api.engagement;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

import org.junit.jupiter.api.Test;

class EngagementRequestValidationTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void feedbackRatingMustBeBetweenOneAndFive() {
        assertThat(validator.validate(new FeedbackRequest(0, null))).isNotEmpty();
        assertThat(validator.validate(new FeedbackRequest(6, null))).isNotEmpty();
        assertThat(validator.validate(new FeedbackRequest(5, "ok"))).isEmpty();
    }

    @Test
    void usageRequiresDateAndCompletedValue() {
        assertThat(validator.validate(new ProductUsageRequest(null, null))).hasSize(2);
        assertThat(validator.validate(new ProductUsageRequest(LocalDate.now(), false))).isEmpty();
    }

    @Test
    void sosRequiresMessageAndValidOptionalSymptomLabels() {
        assertThat(validator.validate(new CreateSosReportRequest(null, "", List.of("REDNESS")))).isNotEmpty();
        assertThat(validator.validate(new CreateSosReportRequest(null, "help", List.of("not valid!")))).isNotEmpty();
        assertThat(validator.validate(new CreateSosReportRequest(null, "help", List.of("REDNESS")))).isEmpty();
    }
}
