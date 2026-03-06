package ru.coproducts.moysklad.reco.recommendation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import ru.coproducts.moysklad.reco.domain.AccountSettingsService;

@Service
public class RecommendationRebuildCoordinator {

    private static final Logger log = LoggerFactory.getLogger(RecommendationRebuildCoordinator.class);

    private final AccountSettingsService settingsService;
    private final RecommendationService recommendationService;
    private final TaskExecutor taskExecutor;

    public RecommendationRebuildCoordinator(AccountSettingsService settingsService,
                                            RecommendationService recommendationService,
                                            @Qualifier("recommendationRebuildTaskExecutor")
                                            TaskExecutor taskExecutor) {
        this.settingsService = settingsService;
        this.recommendationService = recommendationService;
        this.taskExecutor = taskExecutor;
    }

    public RebuildStatus getStatus(String accountId) {
        var state = settingsService.getRebuildState(accountId);
        return new RebuildStatus(state.inProgress(), state.startedAt(), state.finishedAt());
    }

    public RebuildStatus requestManualRebuild(String accountId) {
        return requestRebuild(accountId, "manual");
    }

    public RebuildStatus requestScheduledRebuild(String accountId) {
        return requestRebuild(accountId, "cron");
    }

    private RebuildStatus requestRebuild(String accountId, String source) {
        var attempt = settingsService.tryStartRebuild(accountId);
        var state = attempt.state();
        if (!state.inProgress() || state.startedAt() == null) {
            throw new IllegalStateException("Unexpected rebuild state for accountId=" + accountId);
        }
        if (!attempt.acquired()) {
            log.info("Skipped {} rebuild for accountId={}, rebuild already in progress", source, accountId);
            return new RebuildStatus(true, state.startedAt(), state.finishedAt());
        }

        try {
            taskExecutor.execute(() -> runRebuild(accountId, source));
        } catch (RuntimeException e) {
            settingsService.finishRebuild(accountId);
            throw e;
        }
        return new RebuildStatus(true, state.startedAt(), state.finishedAt());
    }

    private void runRebuild(String accountId, String source) {
        try {
            log.info("Starting {} rebuild for accountId={}", source, accountId);
            recommendationService.rebuildRecommendationsForAccount(accountId);
            log.info("Finished {} rebuild for accountId={}", source, accountId);
        } catch (Exception e) {
            log.warn("Failed {} rebuild for accountId={}: {}", source, accountId, e.getMessage(), e);
        } finally {
            settingsService.finishRebuild(accountId);
        }
    }

    public record RebuildStatus(boolean inProgress, java.time.Instant startedAt, java.time.Instant finishedAt) {
    }
}
