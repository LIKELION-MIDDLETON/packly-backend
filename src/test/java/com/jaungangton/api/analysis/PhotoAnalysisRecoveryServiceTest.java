package com.jaungangton.api.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.jaungangton.api.recommendation.RecommendationWorkflowService;

import tools.jackson.databind.ObjectMapper;

class PhotoAnalysisRecoveryServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-21T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void recoversBothStatesWithoutReanalyzingRecommendationJobsOrDuplicatingSubmissions() {
        AnalysisJobRepository repository = mock(AnalysisJobRepository.class);
        PhotoAnalysisWorkflowService photoWorkflow = mock(PhotoAnalysisWorkflowService.class);
        RecommendationWorkflowService recommendationWorkflow = mock(RecommendationWorkflowService.class);
        AnalysisResultAcceptanceService acceptanceService = mock(AnalysisResultAcceptanceService.class);
        Executor executor = mock(Executor.class);
        UUID analyzingId = UUID.randomUUID();
        UUID recommendingId = UUID.randomUUID();
        UUID legacyRecommendingId = UUID.randomUUID();
        UUID completedId = UUID.randomUUID();
        UUID failedId = UUID.randomUUID();
        UUID analyzingWithoutPhotoId = UUID.randomUUID();

        AnalysisJob analyzing = analyzingJob(analyzingId);
        AnalysisJob recommending = recommendingJob(recommendingId);
        AnalysisJob legacyRecommending = job(legacyRecommendingId);
        legacyRecommending.acceptPhotoResult("legacy-source", Instant.now(CLOCK));
        AnalysisJob completed = recommendingJob(completedId);
        completed.complete(Instant.now(CLOCK));
        AnalysisJob failed = recommendingJob(failedId);
        failed.fail("AI_FAILURE", Instant.now(CLOCK));
        AnalysisJob analyzingWithoutPhoto = job(analyzingWithoutPhotoId);

        List<AnalysisJob> candidates = List.of(
                analyzing, recommending, legacyRecommending, completed, failed, analyzingWithoutPhoto);
        Map<UUID, AnalysisJob> jobs = Map.of(
                analyzingId, analyzing,
                recommendingId, recommending,
                legacyRecommendingId, legacyRecommending,
                completedId, completed,
                failedId, failed,
                analyzingWithoutPhotoId, analyzingWithoutPhoto);
        when(repository.findRecoveryCandidates(AnalysisStatus.ANALYZING, AnalysisStatus.RECOMMENDING))
                .thenReturn(candidates);
        when(repository.findById(any())).thenAnswer(invocation ->
                Optional.ofNullable(jobs.get(invocation.getArgument(0))));
        when(recommendationWorkflow.process(any())).thenReturn(Optional.empty());
        PhotoAnalysisRecoveryService service = service(
                repository, photoWorkflow, recommendationWorkflow, acceptanceService, executor);

        service.recoverInFlightAnalyses(null);
        service.recoverInFlightAnalyses(null);

        ArgumentCaptor<Runnable> tasks = ArgumentCaptor.forClass(Runnable.class);
        verify(executor, times(3)).execute(tasks.capture());
        tasks.getAllValues().forEach(Runnable::run);

        verify(photoWorkflow).process(analyzingId);
        verify(photoWorkflow, never()).process(recommendingId);
        ArgumentCaptor<RecommendationWork> work = ArgumentCaptor.forClass(RecommendationWork.class);
        verify(recommendationWorkflow).process(work.capture());
        assertThat(work.getValue().analysisId()).isEqualTo(recommendingId);
        assertThat(work.getValue().cnnResult().get("label").asText()).isEqualTo("normal");
        assertThat(work.getValue().llmResult().get("summary").asText()).isEqualTo("stored");
        assertThat(work.getValue().survey().get("skin_type").asInt()).isEqualTo(1);
        verify(acceptanceService).markFailed(legacyRecommendingId,
                PhotoAnalysisRecoveryService.RECOVERY_INPUT_MISSING);
        verify(photoWorkflow, never()).process(completedId);
        verify(photoWorkflow, never()).process(failedId);
        verify(recommendationWorkflow, times(1)).process(any(RecommendationWork.class));
        verify(repository, times(2))
                .findRecoveryCandidates(AnalysisStatus.ANALYZING, AnalysisStatus.RECOMMENDING);
    }

    @Test
    void isolatesOneRecommendationRecoveryExceptionFromOtherJobs() {
        AnalysisJobRepository repository = mock(AnalysisJobRepository.class);
        PhotoAnalysisWorkflowService photoWorkflow = mock(PhotoAnalysisWorkflowService.class);
        RecommendationWorkflowService recommendationWorkflow = mock(RecommendationWorkflowService.class);
        AnalysisResultAcceptanceService acceptanceService = mock(AnalysisResultAcceptanceService.class);
        Executor executor = mock(Executor.class);
        UUID failingId = UUID.randomUUID();
        UUID succeedingId = UUID.randomUUID();
        AnalysisJob failing = recommendingJob(failingId);
        AnalysisJob succeeding = recommendingJob(succeedingId);
        Map<UUID, AnalysisJob> jobs = Map.of(failingId, failing, succeedingId, succeeding);
        when(repository.findRecoveryCandidates(AnalysisStatus.ANALYZING, AnalysisStatus.RECOMMENDING))
                .thenReturn(List.of(failing, succeeding));
        when(repository.findById(any())).thenAnswer(invocation ->
                Optional.ofNullable(jobs.get(invocation.getArgument(0))));
        doAnswer(invocation -> {
            RecommendationWork work = invocation.getArgument(0);
            if (work.analysisId().equals(failingId)) {
                throw new IllegalStateException("one recovery failed");
            }
            return Optional.empty();
        }).when(recommendationWorkflow).process(any());
        PhotoAnalysisRecoveryService service = service(
                repository, photoWorkflow, recommendationWorkflow, acceptanceService, executor);

        service.recoverInFlightAnalyses(null);

        ArgumentCaptor<Runnable> tasks = ArgumentCaptor.forClass(Runnable.class);
        verify(executor, times(2)).execute(tasks.capture());
        tasks.getAllValues().forEach(task -> assertThatCode(task::run).doesNotThrowAnyException());
        verify(recommendationWorkflow, times(2)).process(any(RecommendationWork.class));
        verify(photoWorkflow, never()).process(any());
        verify(acceptanceService).markFailed(failingId, "RECOVERY_FAILED");
    }

    private PhotoAnalysisRecoveryService service(
            AnalysisJobRepository repository,
            PhotoAnalysisWorkflowService photoWorkflow,
            RecommendationWorkflowService recommendationWorkflow,
            AnalysisResultAcceptanceService acceptanceService,
            Executor executor) {
        return new PhotoAnalysisRecoveryService(
                repository,
                photoWorkflow,
                recommendationWorkflow,
                acceptanceService,
                new ObjectMapper(),
                executor);
    }

    private AnalysisJob analyzingJob(UUID analysisId) {
        AnalysisJob job = job(analysisId);
        job.attachPhoto(PhotoStorageReference.database(new byte[] {1, 2, 3}, "image/jpeg"), Instant.now(CLOCK));
        return job;
    }

    private AnalysisJob recommendingJob(UUID analysisId) {
        AnalysisJob job = job(analysisId);
        job.acceptPhotoResult(
                "source-" + analysisId,
                "{\"label\":\"normal\"}",
                "{\"summary\":\"stored\"}",
                "{\"skin_type\":1}",
                Instant.now(CLOCK));
        return job;
    }

    private AnalysisJob job(UUID analysisId) {
        return new AnalysisJob(
                analysisId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "{\"skinType\":1}",
                null,
                "recovery-" + analysisId,
                Instant.now(CLOCK));
    }
}
