package ru.coproducts.moysklad.reco.domain;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class AccountSettingsService {

    private final AccountSettingsRepository repository;

    public AccountSettingsService(AccountSettingsRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public AccountSettings getOrDefault(String accountId) {
        return repository.findByAccountId(accountId)
                .orElseGet(() -> {
                    AccountSettings settings = new AccountSettings();
                    settings.setAccountId(accountId);
                    return settings;
                });
    }

    @Transactional
    public AccountSettings save(String accountId, int analysisDays, int minSupport, int limit) {
        AccountSettings settings = repository.findByAccountId(accountId)
                .orElseGet(AccountSettings::new);
        settings.setAccountId(accountId);
        settings.setAnalysisDays(analysisDays);
        settings.setMinSupport(minSupport);
        settings.setLimit(limit);
        return repository.save(settings);
    }

    @Transactional
    public void save(AccountSettings settings) {
        repository.save(settings);
    }

    @Transactional(readOnly = true)
    public RebuildState getRebuildState(String accountId) {
        AccountSettings settings = getOrDefault(accountId);
        return new RebuildState(
                settings.isRebuildInProgress(),
                settings.getRebuildStartedAt(),
                settings.getRebuildFinishedAt()
        );
    }

    @Transactional
    public RebuildAttempt tryStartRebuild(String accountId) {
        AccountSettings settings = findOrCreateForUpdate(accountId);
        if (settings.isRebuildInProgress()) {
            return new RebuildAttempt(
                    false,
                    new RebuildState(true, settings.getRebuildStartedAt(), settings.getRebuildFinishedAt())
            );
        }

        settings.setRebuildInProgress(true);
        settings.setRebuildStartedAt(Instant.now());
        settings.setRebuildFinishedAt(null);
        repository.save(settings);

        return new RebuildAttempt(
                true,
                new RebuildState(true, settings.getRebuildStartedAt(), settings.getRebuildFinishedAt())
        );
    }

    @Transactional
    public RebuildState finishRebuild(String accountId) {
        AccountSettings settings = findOrCreateForUpdate(accountId);
        settings.setRebuildInProgress(false);
        settings.setRebuildFinishedAt(Instant.now());
        repository.save(settings);

        return new RebuildState(false, settings.getRebuildStartedAt(), settings.getRebuildFinishedAt());
    }

    private AccountSettings findOrCreateForUpdate(String accountId) {
        return repository.findByAccountIdForUpdate(accountId)
                .orElseGet(() -> {
                    AccountSettings created = new AccountSettings();
                    created.setAccountId(accountId);
                    try {
                        repository.saveAndFlush(created);
                    } catch (DataIntegrityViolationException ignored) {
                        // Row was created concurrently; fetch it under lock below.
                    }
                    return repository.findByAccountIdForUpdate(accountId)
                            .orElseThrow(() -> new IllegalStateException("Failed to lock account settings for " + accountId));
                });
    }

    public record RebuildState(boolean inProgress, Instant startedAt, Instant finishedAt) {
    }

    public record RebuildAttempt(boolean acquired, RebuildState state) {
    }
}
