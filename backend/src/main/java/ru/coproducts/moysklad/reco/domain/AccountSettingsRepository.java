package ru.coproducts.moysklad.reco.domain;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AccountSettingsRepository extends JpaRepository<AccountSettings, Long> {

    Optional<AccountSettings> findByAccountId(String accountId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from AccountSettings s where s.accountId = :accountId")
    Optional<AccountSettings> findByAccountIdForUpdate(@Param("accountId") String accountId);
}
