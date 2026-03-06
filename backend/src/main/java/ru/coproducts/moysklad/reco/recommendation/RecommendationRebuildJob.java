package ru.coproducts.moysklad.reco.recommendation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.coproducts.moysklad.reco.domain.AccountInstallation;
import ru.coproducts.moysklad.reco.domain.AccountInstallationRepository;
import ru.coproducts.moysklad.reco.domain.AccountSettings;
import ru.coproducts.moysklad.reco.domain.AccountSettingsService;

import java.util.List;

@Component
public class RecommendationRebuildJob {

    private static final Logger log = LoggerFactory.getLogger(RecommendationRebuildJob.class);

    private final AccountInstallationRepository installationRepository;
    private final RecommendationRebuildCoordinator rebuildCoordinator;

    public RecommendationRebuildJob(AccountInstallationRepository installationRepository,
                                    RecommendationRebuildCoordinator rebuildCoordinator) {
        this.installationRepository = installationRepository;
        this.rebuildCoordinator = rebuildCoordinator;
    }

    /**
     * Ночная пересборка рекомендаций.
     * Запускается раз в сутки (03:30 по серверному времени).
     */
    @Scheduled(cron = "0 30 3 * * *")
    public void rebuildAllAccounts() {
        List<AccountInstallation> activeInstallations = installationRepository.findByActiveTrue();

        for (AccountInstallation installation : activeInstallations) {
            String accountId = installation.getAccountId();
            try {
                rebuildCoordinator.requestScheduledRebuild(accountId);
            } catch (Exception e) {
                log.warn("Failed to rebuild recommendations for accountId={}: {}", accountId, e.getMessage());
            }
        }
    }
}
