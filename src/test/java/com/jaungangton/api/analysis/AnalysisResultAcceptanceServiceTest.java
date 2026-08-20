package com.jaungangton.api.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import tools.jackson.databind.ObjectMapper;

class AnalysisResultAcceptanceServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-19T02:00:00Z"), ZoneOffset.UTC);

    @Test
    void acceptsTrustedCnnResultOnceAndTransitionsToRecommending() {
        AnalysisJobRepository repository = mock(AnalysisJobRepository.class);
        UUID analysisId = UUID.randomUUID();
        AnalysisJob job = job(analysisId);
        when(repository.findBySourceResultId("cnn-1")).thenReturn(Optional.empty());
        when(repository.findForUpdateById(analysisId)).thenReturn(Optional.of(job));
        when(repository.save(any(AnalysisJob.class))).thenAnswer(invocation -> invocation.getArgument(0));
        AnalysisResultAcceptanceService service = new AnalysisResultAcceptanceService(repository, CLOCK);
        PhotoAnalysisResult result = new PhotoAnalysisResult(
                "cnn-1", new ObjectMapper().readTree("{\"result\":\"real-provider-value\"}"), null);

        RecommendationWork work = service.acceptPhotoAnalysisResult(analysisId, result);

        assertThat(work.duplicate()).isFalse();
        assertThat(job.status()).isEqualTo(AnalysisStatus.RECOMMENDING);
        assertThat(job.sourceResultId()).isEqualTo("cnn-1");
        assertThat(job.cnnResultJson()).isEqualTo("{\"result\":\"real-provider-value\"}");
        assertThat(job.llmResultJson()).isNull();
        assertThat(job.surveyResultJson()).isNull();
    }

    @Test
    void persistsAllRecommendationInputSnapshotsWithTheRecommendingTransition() {
        AnalysisJobRepository repository = mock(AnalysisJobRepository.class);
        UUID analysisId = UUID.randomUUID();
        AnalysisJob job = job(analysisId);
        when(repository.findBySourceResultId("cnn-2")).thenReturn(Optional.empty());
        when(repository.findForUpdateById(analysisId)).thenReturn(Optional.of(job));
        when(repository.save(any(AnalysisJob.class))).thenAnswer(invocation -> invocation.getArgument(0));
        AnalysisResultAcceptanceService service = new AnalysisResultAcceptanceService(repository, CLOCK);
        PhotoAnalysisResult result = new PhotoAnalysisResult(
                "cnn-2",
                new ObjectMapper().readTree("{\"cnn\":true}"),
                new ObjectMapper().readTree("{\"llm\":true}"),
                new ObjectMapper().readTree("{\"survey\":true}"));

        service.acceptPhotoAnalysisResult(analysisId, result);

        assertThat(job.cnnResultJson()).isEqualTo("{\"cnn\":true}");
        assertThat(job.llmResultJson()).isEqualTo("{\"llm\":true}");
        assertThat(job.surveyResultJson()).isEqualTo("{\"survey\":true}");
        verify(repository).save(job);
    }

    @Test
    void duplicateSourceResultDoesNotCreateAnotherTransition() {
        AnalysisJobRepository repository = mock(AnalysisJobRepository.class);
        UUID analysisId = UUID.randomUUID();
        AnalysisJob job = job(analysisId);
        job.acceptPhotoResult("cnn-1", Instant.now(CLOCK));
        when(repository.findBySourceResultId("cnn-1")).thenReturn(Optional.of(job));
        AnalysisResultAcceptanceService service = new AnalysisResultAcceptanceService(repository, CLOCK);
        PhotoAnalysisResult result = new PhotoAnalysisResult(
                "cnn-1", new ObjectMapper().readTree("{\"result\":1}"), null);

        RecommendationWork work = service.acceptPhotoAnalysisResult(analysisId, result);

        assertThat(work.duplicate()).isTrue();
        assertThat(job.status()).isEqualTo(AnalysisStatus.RECOMMENDING);
    }

    @Test
    void deletesPhotoAfterCompletion() {
        AnalysisJobRepository repository = mock(AnalysisJobRepository.class);
        PhotoStoragePort storage = mock(PhotoStoragePort.class);
        UUID analysisId = UUID.randomUUID();
        AnalysisJob job = job(analysisId);
        PhotoStorageReference reference = PhotoStorageReference.object(
                "analysis-photos/" + analysisId + "/source", new byte[] {1}, "image/jpeg");
        job.attachPhoto(reference, Instant.now(CLOCK));
        job.acceptPhotoResult("cnn-1", Instant.now(CLOCK));
        when(repository.findById(analysisId)).thenReturn(Optional.of(job));
        AnalysisResultAcceptanceService service = new AnalysisResultAcceptanceService(
                repository, null, CLOCK, storage);

        service.markCompleted(analysisId);

        assertThat(job.status()).isEqualTo(AnalysisStatus.COMPLETED);
        assertThat(job.hasPhoto()).isFalse();
        verify(storage).delete(any(PhotoStorageReference.class));
    }

    @Test
    void defersObjectDeletionUntilDatabaseTransactionCommits() {
        AnalysisJobRepository repository = mock(AnalysisJobRepository.class);
        PhotoStoragePort storage = mock(PhotoStoragePort.class);
        UUID analysisId = UUID.randomUUID();
        AnalysisJob job = job(analysisId);
        PhotoStorageReference reference = PhotoStorageReference.object(
                "analysis-photos/" + analysisId + "/source", new byte[] {1}, "image/jpeg");
        job.attachPhoto(reference, Instant.now(CLOCK));
        job.acceptPhotoResult("cnn-1", Instant.now(CLOCK));
        when(repository.findById(analysisId)).thenReturn(Optional.of(job));
        AnalysisResultAcceptanceService service = new AnalysisResultAcceptanceService(
                repository, null, CLOCK, storage);

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.markCompleted(analysisId);

            verify(storage, never()).delete(any());
            for (TransactionSynchronization synchronization
                    : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCommit();
            }
            verify(storage).delete(reference);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void terminalStateIsCommittedEvenWhenObjectCleanupFails() {
        AnalysisJobRepository repository = mock(AnalysisJobRepository.class);
        PhotoStoragePort storage = mock(PhotoStoragePort.class);
        UUID analysisId = UUID.randomUUID();
        AnalysisJob job = job(analysisId);
        PhotoStorageReference reference = PhotoStorageReference.object(
                "analysis-photos/" + analysisId + "/source", new byte[] {1}, "image/jpeg");
        job.attachPhoto(reference, Instant.now(CLOCK));
        when(repository.findById(analysisId)).thenReturn(Optional.of(job));
        doThrow(new PhotoStorageException("temporary failure")).when(storage).delete(any());
        AnalysisResultAcceptanceService service = new AnalysisResultAcceptanceService(
                repository, null, CLOCK, storage);

        service.markFailed(analysisId, "AI_TIMEOUT");

        assertThat(job.status()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(job.hasPhoto()).isFalse();
        verify(storage).delete(reference);
    }

    @Test
    void deletesPhotoAfterFailure() {
        AnalysisJobRepository repository = mock(AnalysisJobRepository.class);
        PhotoStoragePort storage = mock(PhotoStoragePort.class);
        UUID analysisId = UUID.randomUUID();
        AnalysisJob job = job(analysisId);
        job.attachPhoto(PhotoStorageReference.database(new byte[] {1}, "image/jpeg"), Instant.now(CLOCK));
        when(repository.findById(analysisId)).thenReturn(Optional.of(job));
        AnalysisResultAcceptanceService service = new AnalysisResultAcceptanceService(
                repository, null, CLOCK, storage);

        service.markFailed(analysisId, "AI_TIMEOUT");

        assertThat(job.status()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(job.hasPhoto()).isFalse();
        verify(storage).delete(any(PhotoStorageReference.class));
    }

    private AnalysisJob job(UUID analysisId) {
        return new AnalysisJob(
                analysisId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "{\"skinType\":1}",
                null,
                "request-1",
                Instant.now(CLOCK));
    }
}
