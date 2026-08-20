package com.jaungangton.api.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.jaungangton.api.common.ApiException;
import com.jaungangton.api.survey.Survey;
import com.jaungangton.api.survey.SurveyNumericSnapshot;
import com.jaungangton.api.survey.SurveyService;

import tools.jackson.databind.ObjectMapper;

class AnalysisServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-19T01:00:00Z"), ZoneOffset.UTC);

    @Test
    void createsWaitingJobWithCurrentSurveySnapshot() {
        AnalysisJobRepository repository = mock(AnalysisJobRepository.class);
        SurveyService surveyService = mock(SurveyService.class);
        Survey survey = mock(Survey.class);
        UUID userId = UUID.randomUUID();
        UUID surveyId = UUID.randomUUID();
        when(repository.findByUserIdAndIdempotencyKey(userId, "request-1")).thenReturn(Optional.empty());
        when(surveyService.requireEntity(userId)).thenReturn(survey);
        when(survey.id()).thenReturn(surveyId);
        when(surveyService.numericSnapshot(survey)).thenReturn(
                new SurveyNumericSnapshot(1, List.of(2, 4), 3, List.of(3), 2, 1));
        when(repository.save(any(AnalysisJob.class))).thenAnswer(invocation -> invocation.getArgument(0));
        AnalysisService service = new AnalysisService(repository, surveyService, new ObjectMapper(), CLOCK);

        AnalysisResponse response = service.create(userId, " request-1 ", new CreateAnalysisRequest(50_000L));

        ArgumentCaptor<AnalysisJob> captor = ArgumentCaptor.forClass(AnalysisJob.class);
        verify(repository).save(captor.capture());
        assertThat(response.status()).isEqualTo(AnalysisStatus.WAITING_FOR_PHOTO_ANALYSIS);
        assertThat(captor.getValue().surveySnapshot())
                .isEqualTo("{\"skinType\":1,\"concerns\":[2,4],\"duration\":3,\"areas\":[3],\"irritation\":2,\"diagnosed\":1}");
    }

    @Test
    void repeatedIdempotencyKeyReturnsExistingJobWithoutCreatingAnother() {
        AnalysisJobRepository repository = mock(AnalysisJobRepository.class);
        SurveyService surveyService = mock(SurveyService.class);
        UUID userId = UUID.randomUUID();
        AnalysisJob existing = new AnalysisJob(
                UUID.randomUUID(), userId, UUID.randomUUID(), "{}", null, "same", Instant.now(CLOCK));
        when(repository.findByUserIdAndIdempotencyKey(userId, "same")).thenReturn(Optional.of(existing));
        AnalysisService service = new AnalysisService(repository, surveyService, new ObjectMapper(), CLOCK);

        AnalysisResponse response = service.create(userId, "same", new CreateAnalysisRequest(null));

        assertThat(response.id()).isEqualTo(existing.id());
        verify(repository, never()).save(any());
        verify(surveyService, never()).requireEntity(any());
    }

    @Test
    void lookupDoesNotRevealAnotherUsersJob() {
        AnalysisJobRepository repository = mock(AnalysisJobRepository.class);
        UUID userId = UUID.randomUUID();
        UUID analysisId = UUID.randomUUID();
        when(repository.findByIdAndUserId(analysisId, userId)).thenReturn(Optional.empty());
        AnalysisService service = new AnalysisService(repository, mock(SurveyService.class), new ObjectMapper(), CLOCK);

        assertThatThrownBy(() -> service.get(userId, analysisId))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).code())
                .isEqualTo("ANALYSIS_NOT_FOUND");
    }

    @Test
    void storesAndLoadsPhotoThroughSelectedStorage() {
        AnalysisJobRepository repository = mock(AnalysisJobRepository.class);
        PhotoStoragePort storage = mock(PhotoStoragePort.class);
        UUID userId = UUID.randomUUID();
        UUID analysisId = UUID.randomUUID();
        byte[] uploaded = {1, 2, 3};
        byte[] downloaded = {4, 5, 6};
        AnalysisJob job = new AnalysisJob(
                analysisId, userId, UUID.randomUUID(), "{}", null, "photo", Instant.now(CLOCK));
        PhotoStorageReference reference = PhotoStorageReference.object(
                "analysis-photos/" + analysisId + "/source", uploaded, "image/jpeg");
        when(repository.findForUpdateByIdAndUserId(analysisId, userId)).thenReturn(Optional.of(job));
        when(repository.findById(analysisId)).thenReturn(Optional.of(job));
        when(repository.save(job)).thenReturn(job);
        when(storage.store(analysisId, uploaded, "image/jpeg")).thenReturn(reference);
        when(storage.load(any(PhotoStorageReference.class))).thenReturn(downloaded);
        AnalysisService service = new AnalysisService(
                repository, mock(SurveyService.class), new ObjectMapper(), CLOCK, null, storage);

        PhotoUploadResult upload = service.attachPhoto(userId, analysisId, uploaded, "image/jpeg");
        PhotoAnalysisInput input = service.photoInput(analysisId);

        assertThat(upload.started()).isTrue();
        assertThat(input.photoData()).containsExactly(downloaded);
        assertThat(input.contentType()).isEqualTo("image/jpeg");
        verify(storage).store(analysisId, uploaded, "image/jpeg");
        verify(storage).load(any(PhotoStorageReference.class));
    }

    @Test
    void mapsStorageUploadFailureToStableServiceUnavailableError() {
        AnalysisJobRepository repository = mock(AnalysisJobRepository.class);
        PhotoStoragePort storage = mock(PhotoStoragePort.class);
        UUID userId = UUID.randomUUID();
        UUID analysisId = UUID.randomUUID();
        AnalysisJob job = new AnalysisJob(
                analysisId, userId, UUID.randomUUID(), "{}", null, "photo", Instant.now(CLOCK));
        when(repository.findForUpdateByIdAndUserId(analysisId, userId)).thenReturn(Optional.of(job));
        when(storage.store(any(), any(), any())).thenThrow(new PhotoStorageException("provider detail"));
        AnalysisService service = new AnalysisService(
                repository, mock(SurveyService.class), new ObjectMapper(), CLOCK, null, storage);

        assertThatThrownBy(() -> service.attachPhoto(userId, analysisId, new byte[] {1}, "image/jpeg"))
                .isInstanceOf(ApiException.class)
                .satisfies(exception -> {
                    ApiException apiException = (ApiException) exception;
                    assertThat(apiException.code()).isEqualTo("PHOTO_STORAGE_UNAVAILABLE");
                    assertThat(apiException.status()).isEqualTo(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(apiException.getMessage()).doesNotContain("provider detail");
                });
    }

    @Test
    void removesNewObjectWhenConcurrentAttachmentAlreadyStartedTheJob() {
        AnalysisJobRepository repository = mock(AnalysisJobRepository.class);
        PhotoStoragePort storage = mock(PhotoStoragePort.class);
        PhotoAttachmentPersistenceService persistence = mock(PhotoAttachmentPersistenceService.class);
        UUID userId = UUID.randomUUID();
        UUID analysisId = UUID.randomUUID();
        byte[] uploaded = {1, 2, 3};
        AnalysisJob job = new AnalysisJob(
                analysisId, userId, UUID.randomUUID(), "{}", null, "photo", Instant.now(CLOCK));
        PhotoStorageReference reference = PhotoStorageReference.object(
                "analysis-photos/" + analysisId + "/unique", uploaded, "image/jpeg");
        when(storage.store(analysisId, uploaded, "image/jpeg")).thenReturn(reference);
        when(persistence.attach(userId, analysisId, reference))
                .thenReturn(new PhotoUploadResult(AnalysisResponse.from(job), false));
        AnalysisService service = new AnalysisService(
                repository, mock(SurveyService.class), new ObjectMapper(), CLOCK, null, storage, persistence);

        PhotoUploadResult result = service.attachPhoto(userId, analysisId, uploaded, "image/jpeg");

        assertThat(result.started()).isFalse();
        verify(storage).delete(reference);
    }

    @Test
    void removesNewObjectWhenDatabaseAttachmentOrCommitFails() {
        AnalysisJobRepository repository = mock(AnalysisJobRepository.class);
        PhotoStoragePort storage = mock(PhotoStoragePort.class);
        PhotoAttachmentPersistenceService persistence = mock(PhotoAttachmentPersistenceService.class);
        UUID userId = UUID.randomUUID();
        UUID analysisId = UUID.randomUUID();
        byte[] uploaded = {1, 2, 3};
        PhotoStorageReference reference = PhotoStorageReference.object(
                "analysis-photos/" + analysisId + "/unique", uploaded, "image/jpeg");
        when(storage.store(analysisId, uploaded, "image/jpeg")).thenReturn(reference);
        when(persistence.attach(userId, analysisId, reference))
                .thenThrow(new IllegalStateException("database commit failed"));
        AnalysisService service = new AnalysisService(
                repository, mock(SurveyService.class), new ObjectMapper(), CLOCK, null, storage, persistence);

        assertThatThrownBy(() -> service.attachPhoto(userId, analysisId, uploaded, "image/jpeg"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("database commit failed");
        verify(storage).delete(reference);
    }
}
