package ru.coproducts.moysklad.reco.recommendation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "base_product_stats",
       uniqueConstraints = @UniqueConstraint(
               name = "uq_base_product_stats_account_version_base",
               columnNames = {"account_id", "snapshot_version", "base_product_id"}
       ),
       indexes = @Index(
               name = "idx_base_product_stats_account_version_base",
               columnList = "account_id, snapshot_version, base_product_id"
       ))
public class BaseProductStats {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private String accountId;

    @Column(name = "base_product_id", nullable = false)
    private String baseProductId;

    @Column(name = "snapshot_version", nullable = false)
    private long snapshotVersion;

    @Column(name = "docs_with_base", nullable = false)
    private long docsWithBase;

    public Long getId() {
        return id;
    }

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public String getBaseProductId() {
        return baseProductId;
    }

    public void setBaseProductId(String baseProductId) {
        this.baseProductId = baseProductId;
    }

    public long getSnapshotVersion() {
        return snapshotVersion;
    }

    public void setSnapshotVersion(long snapshotVersion) {
        this.snapshotVersion = snapshotVersion;
    }

    public long getDocsWithBase() {
        return docsWithBase;
    }

    public void setDocsWithBase(long docsWithBase) {
        this.docsWithBase = docsWithBase;
    }
}
