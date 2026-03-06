package ru.coproducts.moysklad.reco.recommendation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RecommendationPairRepository extends JpaRepository<RecommendationPair, Long> {

    List<RecommendationPair> findByAccountIdAndSnapshotVersionAndBaseProductIdOrderBySupportCountDesc(
            String accountId,
            long snapshotVersion,
            String baseProductId
    );

    Optional<RecommendationPair> findByAccountIdAndSnapshotVersionAndBaseProductIdAndRecommendedProductId(
            String accountId,
            long snapshotVersion,
            String baseProductId,
            String recommendedProductId
    );

    List<RecommendationPair> findByAccountIdAndSnapshotVersionAndBaseProductId(
            String accountId,
            long snapshotVersion,
            String baseProductId
    );

    void deleteByAccountIdAndSnapshotVersionAndBaseProductId(String accountId, long snapshotVersion, String baseProductId);

    void deleteByAccountIdAndSnapshotVersion(String accountId, long snapshotVersion);
}
