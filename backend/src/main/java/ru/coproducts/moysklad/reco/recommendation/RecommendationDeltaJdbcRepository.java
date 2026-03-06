package ru.coproducts.moysklad.reco.recommendation;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Repository
public class RecommendationDeltaJdbcRepository {

    private static final int BATCH_SIZE = 1_000;

    private static final String UPSERT_BASE_STATS_SQL = """
            INSERT INTO base_product_stats (account_id, snapshot_version, base_product_id, docs_with_base)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (account_id, snapshot_version, base_product_id)
            DO UPDATE SET docs_with_base = base_product_stats.docs_with_base + EXCLUDED.docs_with_base
            """;

    private static final String UPSERT_RECOMMENDATION_PAIR_SQL = """
            INSERT INTO recommendation_pairs (
                account_id,
                snapshot_version,
                base_product_id,
                recommended_product_id,
                support_count,
                confidence,
                updated_at
            )
            VALUES (?, ?, ?, ?, ?, 0, now())
            ON CONFLICT (account_id, snapshot_version, base_product_id, recommended_product_id)
            DO UPDATE SET support_count = recommendation_pairs.support_count + EXCLUDED.support_count,
                          updated_at = now()
            """;

    private static final String REFRESH_CONFIDENCE_SQL = """
            UPDATE recommendation_pairs pair
            SET confidence = pair.support_count::double precision / stats.docs_with_base,
                updated_at = now()
            FROM base_product_stats stats
            WHERE pair.account_id = ?
              AND pair.snapshot_version = ?
              AND pair.base_product_id = ?
              AND stats.account_id = ?
              AND stats.snapshot_version = ?
              AND stats.base_product_id = ?
              AND stats.docs_with_base > 0
            """;

    private static final String DELETE_SNAPSHOT_PAIRS_SQL = """
            DELETE FROM recommendation_pairs
            WHERE account_id = ?
              AND snapshot_version = ?
            """;

    private static final String DELETE_SNAPSHOT_BASE_STATS_SQL = """
            DELETE FROM base_product_stats
            WHERE account_id = ?
              AND snapshot_version = ?
            """;

    private static final String DELETE_OLD_PAIRS_SQL = """
            DELETE FROM recommendation_pairs
            WHERE account_id = ?
              AND snapshot_version <> ?
            """;

    private static final String DELETE_OLD_BASE_STATS_SQL = """
            DELETE FROM base_product_stats
            WHERE account_id = ?
              AND snapshot_version <> ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public RecommendationDeltaJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void applyDeltas(String accountId,
                            long snapshotVersion,
                            Map<String, Long> baseDocsDelta,
                            Map<String, Long> supportDelta) {
        if (baseDocsDelta.isEmpty() && supportDelta.isEmpty()) {
            return;
        }

        if (!baseDocsDelta.isEmpty()) {
            List<Map.Entry<String, Long>> baseEntries = new ArrayList<>(baseDocsDelta.entrySet());
            jdbcTemplate.batchUpdate(
                    UPSERT_BASE_STATS_SQL,
                    baseEntries,
                    BATCH_SIZE,
                    (ps, entry) -> {
                        ps.setString(1, accountId);
                        ps.setLong(2, snapshotVersion);
                        ps.setString(3, entry.getKey());
                        ps.setLong(4, entry.getValue());
                    }
            );
        }

        if (!supportDelta.isEmpty()) {
            List<Map.Entry<String, Long>> pairEntries = new ArrayList<>(supportDelta.entrySet());
            jdbcTemplate.batchUpdate(
                    UPSERT_RECOMMENDATION_PAIR_SQL,
                    pairEntries,
                    BATCH_SIZE,
                    (ps, entry) -> {
                        String[] parts = entry.getKey().split("\\|", 2);
                        ps.setString(1, accountId);
                        ps.setLong(2, snapshotVersion);
                        ps.setString(3, parts[0]);
                        ps.setString(4, parts[1]);
                        ps.setLong(5, entry.getValue());
                    }
            );
        }

        if (!baseDocsDelta.isEmpty()) {
            List<String> baseProductIds = new ArrayList<>(baseDocsDelta.keySet());
            jdbcTemplate.batchUpdate(
                    REFRESH_CONFIDENCE_SQL,
                    baseProductIds,
                    BATCH_SIZE,
                    (ps, baseProductId) -> {
                        ps.setString(1, accountId);
                        ps.setLong(2, snapshotVersion);
                        ps.setString(3, baseProductId);
                        ps.setString(4, accountId);
                        ps.setLong(5, snapshotVersion);
                        ps.setString(6, baseProductId);
                    }
            );
        }
    }

    public void deleteSnapshot(String accountId, long snapshotVersion) {
        jdbcTemplate.update(DELETE_SNAPSHOT_PAIRS_SQL, accountId, snapshotVersion);
        jdbcTemplate.update(DELETE_SNAPSHOT_BASE_STATS_SQL, accountId, snapshotVersion);
    }

    public void deleteOtherSnapshots(String accountId, long activeSnapshotVersion) {
        jdbcTemplate.update(DELETE_OLD_PAIRS_SQL, accountId, activeSnapshotVersion);
        jdbcTemplate.update(DELETE_OLD_BASE_STATS_SQL, accountId, activeSnapshotVersion);
    }
}
