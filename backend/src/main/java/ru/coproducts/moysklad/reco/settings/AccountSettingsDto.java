package ru.coproducts.moysklad.reco.settings;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.time.Instant;

public class AccountSettingsDto {

    @Min(1)
    @Max(365)
    private int analysisDays;

    @Min(1)
    @Max(1000)
    private int minSupport;

    @Min(1)
    @Max(100)
    private int limit;

    private boolean rebuildInProgress;

    private Instant rebuildStartedAt;

    private Instant rebuildFinishedAt;

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

    public boolean isRebuildInProgress() {
        return rebuildInProgress;
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
}
