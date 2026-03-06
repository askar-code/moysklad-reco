package ru.coproducts.moysklad.reco.recommendation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(
        name = "recommendation_pairs",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_recommendation_pairs_account_version_base_reco",
                columnNames = {"account_id", "snapshot_version", "base_product_id", "recommended_product_id"}
        ),
        indexes = {
                @Index(
                        name = "idx_recommendation_pairs_account_version_base_support",
                        columnList = "account_id, snapshot_version, base_product_id, support_count"
                )
        }
)
public class RecommendationPair {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private String accountId;

    @Column(name = "base_product_id", nullable = false)
    private String baseProductId;

    @Column(name = "recommended_product_id", nullable = false)
    private String recommendedProductId;

    @Column(name = "snapshot_version", nullable = false)
    private long snapshotVersion;

    @Column(name = "support_count", nullable = false)
    private long supportCount;

    @Column(name = "confidence", nullable = false)
    private double confidence;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    public void touch() {
        this.updatedAt = Instant.now();
    }

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

    public String getRecommendedProductId() {
        return recommendedProductId;
    }

    public void setRecommendedProductId(String recommendedProductId) {
        this.recommendedProductId = recommendedProductId;
    }

    public long getSnapshotVersion() {
        return snapshotVersion;
    }

    public void setSnapshotVersion(long snapshotVersion) {
        this.snapshotVersion = snapshotVersion;
    }

    public long getSupportCount() {
        return supportCount;
    }

    public void setSupportCount(long supportCount) {
        this.supportCount = supportCount;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
