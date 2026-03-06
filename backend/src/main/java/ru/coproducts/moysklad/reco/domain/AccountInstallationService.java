package ru.coproducts.moysklad.reco.domain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.coproducts.moysklad.reco.vendor.dto.VendorActivationRequest;

import java.util.Optional;

@Service
public class AccountInstallationService {

    private static final Logger log = LoggerFactory.getLogger(AccountInstallationService.class);

    private final AccountInstallationRepository repository;

    public AccountInstallationService(AccountInstallationRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public AccountInstallation activate(String accountId, VendorActivationRequest request) {
        log.info("Activating account installation accountId={} appId={} cause={}",
                accountId, request.getAppUid(), request.getCause());

        AccountInstallation installation = repository.findByAccountId(accountId)
                .orElseGet(AccountInstallation::new);

        String accessToken = request.getAccessToken();
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalArgumentException("Missing JSON API access token in Vendor API payload");
        }

        String tariff = request.getSubscription() != null ? request.getSubscription().getTariff() : null;
        if (tariff == null || tariff.isBlank()) {
            tariff = "unknown";
        }

        installation.setAccountId(accountId);
        installation.setAppId(request.getAppUid());
        installation.setAccessToken(accessToken);
        installation.setTariff(tariff);
        installation.setActive(true);
        installation.setLastCause(request.getCause());

        return repository.save(installation);
    }

    @Transactional
    public void deactivate(String accountId) {
        repository.findByAccountId(accountId).ifPresent(installation -> {
            log.info("Deactivating account installation accountId={}", accountId);
            installation.setActive(false);
            repository.save(installation);
        });
    }

    @Transactional(readOnly = true)
    public Optional<AccountInstallation> findActiveByAccountId(String accountId) {
        return repository.findByAccountId(accountId)
                .filter(AccountInstallation::isActive);
    }
}
