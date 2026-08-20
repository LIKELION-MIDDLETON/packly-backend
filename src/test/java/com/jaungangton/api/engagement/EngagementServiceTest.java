package com.jaungangton.api.engagement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.jaungangton.api.common.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class EngagementServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-19T00:00:00Z");
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID RECOMMENDATION_ID = UUID.randomUUID();
    private static final UUID PRODUCT_ID = UUID.randomUUID();

    @Mock
    ProductUsageCompletionRepository usageRepository;
    @Mock
    RecommendationFeedbackRepository feedbackRepository;
    @Mock
    SosReportRepository sosRepository;
    @Mock
    EngagementOwnershipQuery ownership;

    private EngagementService service;

    @BeforeEach
    void setUp() {
        service = new EngagementService(
                usageRepository, feedbackRepository, sosRepository, ownership,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void repeatedUsagePutUpdatesTheSameUserProductDateRecord() {
        LocalDate usedOn = LocalDate.of(2026, 8, 19);
        when(ownership.ownsProduct(USER_ID, RECOMMENDATION_ID, PRODUCT_ID)).thenReturn(true);
        when(usageRepository.findByUserIdAndRecommendationProductIdAndUsedOn(USER_ID, PRODUCT_ID, usedOn))
                .thenReturn(Optional.empty())
                .thenAnswer(invocation -> Optional.of(invocationCompletion));
        when(usageRepository.save(any())).thenAnswer(invocation -> {
            invocationCompletion = invocation.getArgument(0);
            return invocationCompletion;
        });

        ProductUsageResponse created = service.putUsage(
                USER_ID, RECOMMENDATION_ID, PRODUCT_ID, new ProductUsageRequest(usedOn, true));
        ProductUsageResponse repeated = service.putUsage(
                USER_ID, RECOMMENDATION_ID, PRODUCT_ID, new ProductUsageRequest(usedOn, false));

        assertThat(repeated.id()).isEqualTo(created.id());
        assertThat(repeated.completed()).isFalse();
        verify(usageRepository, org.mockito.Mockito.times(2)).save(any());
    }

    @Test
    void identicalUsagePutPreservesTheExistingRecordTimestamp() {
        LocalDate usedOn = LocalDate.of(2026, 8, 19);
        Instant createdAt = NOW.minusSeconds(60);
        ProductUsageCompletion existing = new ProductUsageCompletion(
                UUID.randomUUID(), USER_ID, PRODUCT_ID, usedOn, true, createdAt);
        when(ownership.ownsProduct(USER_ID, RECOMMENDATION_ID, PRODUCT_ID)).thenReturn(true);
        when(usageRepository.findByUserIdAndRecommendationProductIdAndUsedOn(USER_ID, PRODUCT_ID, usedOn))
                .thenReturn(Optional.of(existing));
        when(usageRepository.save(existing)).thenReturn(existing);

        ProductUsageResponse response = service.putUsage(
                USER_ID, RECOMMENDATION_ID, PRODUCT_ID, new ProductUsageRequest(usedOn, true));

        assertThat(response.id()).isEqualTo(existing.id());
        assertThat(response.updatedAt()).isEqualTo(createdAt);
    }

    private ProductUsageCompletion invocationCompletion;

    @Test
    void repeatedFeedbackPutUpsertsOneRecord() {
        RecommendationFeedback existing = new RecommendationFeedback(
                UUID.randomUUID(), USER_ID, RECOMMENDATION_ID, 2, "before", NOW.minusSeconds(60));
        when(ownership.ownsRecommendation(USER_ID, RECOMMENDATION_ID)).thenReturn(true);
        when(feedbackRepository.findByUserIdAndRecommendationId(USER_ID, RECOMMENDATION_ID))
                .thenReturn(Optional.of(existing));
        when(feedbackRepository.save(existing)).thenReturn(existing);

        FeedbackResponse response = service.putFeedback(
                USER_ID, RECOMMENDATION_ID, new FeedbackRequest(5, "  updated  "));

        assertThat(response.id()).isEqualTo(existing.id());
        assertThat(response.rating()).isEqualTo(5);
        assertThat(response.comment()).isEqualTo("updated");
        verify(feedbackRepository).save(existing);
    }

    @Test
    void crossOwnerProductIsReportedAsNotFound() {
        when(ownership.ownsProduct(USER_ID, RECOMMENDATION_ID, PRODUCT_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.putUsage(
                USER_ID, RECOMMENDATION_ID, PRODUCT_ID,
                new ProductUsageRequest(LocalDate.of(2026, 8, 19), true)))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.status()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(usageRepository, never()).save(any());
    }

    @Test
    void crossOwnerRecommendationCannotReceiveFeedbackOrLinkedSos() {
        when(ownership.ownsRecommendation(USER_ID, RECOMMENDATION_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.putFeedback(
                USER_ID, RECOMMENDATION_ID, new FeedbackRequest(4, null)))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.status()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> service.createSos(
                USER_ID, new CreateSosReportRequest(RECOMMENDATION_ID, "help", List.of("REDNESS"))))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.status()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(feedbackRepository, never()).save(any());
        verify(sosRepository, never()).save(any());
    }

    @Test
    void sosIsStoredAsReceivedWithoutDiagnosticFields() {
        when(ownership.ownsRecommendation(USER_ID, RECOMMENDATION_ID)).thenReturn(true);
        when(sosRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SosReportResponse response = service.createSos(
                USER_ID, new CreateSosReportRequest(RECOMMENDATION_ID, "  skin feels hot  ", List.of("REDNESS")));

        assertThat(response.recommendationId()).isEqualTo(RECOMMENDATION_ID);
        assertThat(response.message()).isEqualTo("skin feels hot");
        assertThat(response.symptomLabels()).containsExactly("REDNESS");
        assertThat(response.status()).isEqualTo(SosStatus.RECEIVED);
        assertThat(SosReportResponse.class.getRecordComponents())
                .extracting(component -> component.getName())
                .doesNotContain("diagnosis", "causeProduct", "recoveryRoutine");
    }

    @Test
    void sosReadsAreScopedToCurrentUser() {
        UUID reportId = UUID.randomUUID();
        SosReport report = new SosReport(reportId, USER_ID, null, "help", List.of("ITCHING"), NOW);
        when(sosRepository.findAllByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(List.of(report));
        when(sosRepository.findByIdAndUserId(reportId, USER_ID)).thenReturn(Optional.of(report));

        assertThat(service.listSos(USER_ID)).extracting(SosReportResponse::id).containsExactly(reportId);
        assertThat(service.getSos(USER_ID, reportId).id()).isEqualTo(reportId);

        UUID otherReport = UUID.randomUUID();
        when(sosRepository.findByIdAndUserId(otherReport, USER_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getSos(USER_ID, otherReport))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.status()).isEqualTo(HttpStatus.NOT_FOUND));
    }
}
