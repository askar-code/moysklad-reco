package ru.coproducts.moysklad.reco.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AccountInstallationRepository extends JpaRepository<AccountInstallation, Long> {

    Optional<AccountInstallation> findByAccountId(String accountId);

    List<AccountInstallation> findByActiveTrue();
}

