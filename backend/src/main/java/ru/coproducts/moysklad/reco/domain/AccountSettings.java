package ru.coproducts.moysklad.reco.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "account_settings")
public class AccountSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false, unique = true)
    private String accountId;

    @Column(name = "analysis_days", nullable = false)
    private int analysisDays = 90;

    @Column(name = "min_support", nullable = false)
    private int minSupport = 3;

    @Column(name = "limit_count", nullable = false)
    private int limit = 5;

    @Column(name = "last_demand_moment")
    private Instant lastProcessedDemandMoment;

    @Column(name = "last_retaildemand_moment")
    private Instant lastProcessedRetailDemandMoment;

    @Column(name = "rebuild_in_progress")
    private Boolean rebuildInProgress;

    @Column(name = "rebuild_started_at")
    private Instant rebuildStartedAt;

    @Column(name = "rebuild_finished_at")
    private Instant rebuildFinishedAt;

    @Column(name = "active_recommendation_version", nullable = false)
    private long activeRecommendationVersion;

    public Long getId() {
        return id;
    }

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public int getAnalysisDays() {
        return analysisDays;
    }

    public void setAnalysisDays(int analysisDays) {
        this.analysisDays = analysisDays;
    }

    public int getMinSupport() {
        return minSupport;
    }

    public void setMinSupport(int minSupport) {
        this.minSupport = minSupport;
    }

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }

    public Instant getLastProcessedDemandMoment() {
        return lastProcessedDemandMoment;
    }

    public void setLastProcessedDemandMoment(Instant lastProcessedDemandMoment) {
        this.lastProcessedDemandMoment = lastProcessedDemandMoment;
    }

    public Instant getLastProcessedRetailDemandMoment() {
        return lastProcessedRetailDemandMoment;
    }

    public void setLastProcessedRetailDemandMoment(Instant lastProcessedRetailDemandMoment) {
        this.lastProcessedRetailDemandMoment = lastProcessedRetailDemandMoment;
    }

    public boolean isRebuildInProgress() {
        return Boolean.TRUE.equals(rebuildInProgress);
    }

    public void setRebuildInProgress(boolean rebuildInProgress) {
        this.rebuildInProgress = rebuildInProgress;
    }

    public Instant getRebuildStartedAt() {
        return rebuildStartedAt;
    }

    public void setRebuildStartedAt(Instant rebuildStartedAt) {
        this.rebuildStartedAt = rebuildStartedAt;
    }

    public Instant getRebuildFinishedAt() {
        return rebuildFinishedAt;
    }

    public void setRebuildFinishedAt(Instant rebuildFinishedAt) {
        this.rebuildFinishedAt = rebuildFinishedAt;
    }

    public long getActiveRecommendationVersion() {
        return activeRecommendationVersion;
    }

    public void setActiveRecommendationVersion(long activeRecommendationVersion) {
        this.activeRecommendationVersion = activeRecommendationVersion;
    }
}
