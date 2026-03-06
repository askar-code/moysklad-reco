package ru.coproducts.moysklad.reco.recommendation;

import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;
import ru.coproducts.moysklad.reco.domain.AccountSettingsService;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecommendationRebuildCoordinatorTest {

    private final AccountSettingsService settingsService = mock(AccountSettingsService.class);
    private final RecommendationService recommendationService = mock(RecommendationService.class);
    private final TaskExecutor taskExecutor = Runnable::run;

    @Test
    void startsManualRebuildWhenLockAcquired() {
        RecommendationRebuildCoordinator coordinator =
                new RecommendationRebuildCoordinator(settingsService, recommendationService, taskExecutor);
        Instant startedAt = Instant.parse("2026-03-06T10:00:00Z");

        when(settingsService.tryStartRebuild("acc-1")).thenReturn(
                new AccountSettingsService.RebuildAttempt(
                        true,
                        new AccountSettingsService.RebuildState(true, startedAt, null)
                )
        );

        RecommendationRebuildCoordinator.RebuildStatus status = coordinator.requestManualRebuild("acc-1");

        assertTrue(status.inProgress());
        verify(recommendationService).rebuildRecommendationsForAccount("acc-1");
        verify(settingsService).finishRebuild("acc-1");
    }

    @Test
    void skipsManualRebuildWhenAlreadyRunning() {
        RecommendationRebuildCoordinator coordinator =
                new RecommendationRebuildCoordinator(settingsService, recommendationService, taskExecutor);
        Instant startedAt = Instant.parse("2026-03-06T10:00:00Z");

        when(settingsService.tryStartRebuild("acc-1")).thenReturn(
                new AccountSettingsService.RebuildAttempt(
                        false,
                        new AccountSettingsService.RebuildState(true, startedAt, null)
                )
        );

        RecommendationRebuildCoordinator.RebuildStatus status = coordinator.requestManualRebuild("acc-1");

        assertTrue(status.inProgress());
        verify(recommendationService, never()).rebuildRecommendationsForAccount("acc-1");
        verify(settingsService, never()).finishRebuild("acc-1");
    }

    @Test
    void releasesLockWhenExecutorFails() {
        TaskExecutor failingExecutor = mock(TaskExecutor.class);
        RecommendationRebuildCoordinator coordinator =
                new RecommendationRebuildCoordinator(settingsService, recommendationService, failingExecutor);
        Instant startedAt = Instant.parse("2026-03-06T10:00:00Z");

        when(settingsService.tryStartRebuild("acc-1")).thenReturn(
                new AccountSettingsService.RebuildAttempt(
                        true,
                        new AccountSettingsService.RebuildState(true, startedAt, null)
                )
        );
        doThrow(new RuntimeException("boom")).when(failingExecutor).execute(org.mockito.ArgumentMatchers.any());

        boolean failed = false;
        try {
            coordinator.requestManualRebuild("acc-1");
        } catch (RuntimeException e) {
            failed = true;
        }

        assertTrue(failed);
        verify(settingsService).finishRebuild("acc-1");
        verify(recommendationService, never()).rebuildRecommendationsForAccount("acc-1");
    }
}
