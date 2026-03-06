package ru.coproducts.moysklad.reco.recommendation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BaseProductStatsRepository extends JpaRepository<BaseProductStats, Long> {

    Optional<BaseProductStats> findByAccountIdAndSnapshotVersionAndBaseProductId(
            String accountId,
            long snapshotVersion,
            String baseProductId
    );

    void deleteByAccountIdAndSnapshotVersion(String accountId, long snapshotVersion);
}
