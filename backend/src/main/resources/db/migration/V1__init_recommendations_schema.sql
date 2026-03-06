CREATE TABLE IF NOT EXISTS account_installations (
    id BIGSERIAL PRIMARY KEY,
    account_id VARCHAR(255) NOT NULL,
    app_id VARCHAR(255) NOT NULL,
    access_token VARCHAR(4096) NOT NULL,
    tariff VARCHAR(255) NOT NULL,
    last_cause VARCHAR(255),
    active BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS account_settings (
    id BIGSERIAL PRIMARY KEY,
    account_id VARCHAR(255) NOT NULL,
    analysis_days INTEGER NOT NULL DEFAULT 90,
    min_support INTEGER NOT NULL DEFAULT 3,
    limit_count INTEGER NOT NULL DEFAULT 5,
    last_demand_moment TIMESTAMP(6) WITH TIME ZONE,
    last_retaildemand_moment TIMESTAMP(6) WITH TIME ZONE,
    rebuild_in_progress BOOLEAN,
    rebuild_started_at TIMESTAMP(6) WITH TIME ZONE,
    rebuild_finished_at TIMESTAMP(6) WITH TIME ZONE,
    active_recommendation_version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS recommendation_pairs (
    id BIGSERIAL PRIMARY KEY,
    account_id VARCHAR(255) NOT NULL,
    base_product_id VARCHAR(255) NOT NULL,
    recommended_product_id VARCHAR(255) NOT NULL,
    snapshot_version BIGINT NOT NULL DEFAULT 0,
    support_count BIGINT NOT NULL,
    confidence DOUBLE PRECISION NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS base_product_stats (
    id BIGSERIAL PRIMARY KEY,
    account_id VARCHAR(255) NOT NULL,
    base_product_id VARCHAR(255) NOT NULL,
    snapshot_version BIGINT NOT NULL DEFAULT 0,
    docs_with_base BIGINT NOT NULL
);

ALTER TABLE account_installations
    ADD COLUMN IF NOT EXISTS app_id VARCHAR(255);
ALTER TABLE account_installations
    ADD COLUMN IF NOT EXISTS access_token VARCHAR(4096);
ALTER TABLE account_installations
    ADD COLUMN IF NOT EXISTS tariff VARCHAR(255);
ALTER TABLE account_installations
    ADD COLUMN IF NOT EXISTS last_cause VARCHAR(255);
ALTER TABLE account_installations
    ADD COLUMN IF NOT EXISTS active BOOLEAN;
ALTER TABLE account_installations
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP(6) WITH TIME ZONE;
ALTER TABLE account_installations
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP(6) WITH TIME ZONE;

UPDATE account_installations
SET active = FALSE
WHERE active IS NULL;

ALTER TABLE account_installations
    ALTER COLUMN account_id SET NOT NULL;
ALTER TABLE account_installations
    ALTER COLUMN app_id SET NOT NULL;
ALTER TABLE account_installations
    ALTER COLUMN access_token SET NOT NULL;
ALTER TABLE account_installations
    ALTER COLUMN tariff SET NOT NULL;
ALTER TABLE account_installations
    ALTER COLUMN active SET DEFAULT FALSE;
ALTER TABLE account_installations
    ALTER COLUMN active SET NOT NULL;
ALTER TABLE account_installations
    ALTER COLUMN created_at SET NOT NULL;
ALTER TABLE account_installations
    ALTER COLUMN updated_at SET NOT NULL;

ALTER TABLE account_settings
    ADD COLUMN IF NOT EXISTS analysis_days INTEGER;
ALTER TABLE account_settings
    ADD COLUMN IF NOT EXISTS min_support INTEGER;
ALTER TABLE account_settings
    ADD COLUMN IF NOT EXISTS limit_count INTEGER;
ALTER TABLE account_settings
    ADD COLUMN IF NOT EXISTS last_demand_moment TIMESTAMP(6) WITH TIME ZONE;
ALTER TABLE account_settings
    ADD COLUMN IF NOT EXISTS last_retaildemand_moment TIMESTAMP(6) WITH TIME ZONE;
ALTER TABLE account_settings
    ADD COLUMN IF NOT EXISTS rebuild_in_progress BOOLEAN;
ALTER TABLE account_settings
    ADD COLUMN IF NOT EXISTS rebuild_started_at TIMESTAMP(6) WITH TIME ZONE;
ALTER TABLE account_settings
    ADD COLUMN IF NOT EXISTS rebuild_finished_at TIMESTAMP(6) WITH TIME ZONE;
ALTER TABLE account_settings
    ADD COLUMN IF NOT EXISTS active_recommendation_version BIGINT;

UPDATE account_settings
SET analysis_days = 90
WHERE analysis_days IS NULL;

UPDATE account_settings
SET min_support = 3
WHERE min_support IS NULL;

UPDATE account_settings
SET limit_count = 5
WHERE limit_count IS NULL;

UPDATE account_settings
SET active_recommendation_version = 0
WHERE active_recommendation_version IS NULL;

ALTER TABLE account_settings
    ALTER COLUMN account_id SET NOT NULL;
ALTER TABLE account_settings
    ALTER COLUMN analysis_days SET DEFAULT 90;
ALTER TABLE account_settings
    ALTER COLUMN analysis_days SET NOT NULL;
ALTER TABLE account_settings
    ALTER COLUMN min_support SET DEFAULT 3;
ALTER TABLE account_settings
    ALTER COLUMN min_support SET NOT NULL;
ALTER TABLE account_settings
    ALTER COLUMN limit_count SET DEFAULT 5;
ALTER TABLE account_settings
    ALTER COLUMN limit_count SET NOT NULL;
ALTER TABLE account_settings
    ALTER COLUMN active_recommendation_version SET DEFAULT 0;
ALTER TABLE account_settings
    ALTER COLUMN active_recommendation_version SET NOT NULL;

ALTER TABLE recommendation_pairs
    ADD COLUMN IF NOT EXISTS snapshot_version BIGINT;
ALTER TABLE recommendation_pairs
    ADD COLUMN IF NOT EXISTS support_count BIGINT;
ALTER TABLE recommendation_pairs
    ADD COLUMN IF NOT EXISTS confidence DOUBLE PRECISION;
ALTER TABLE recommendation_pairs
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP(6) WITH TIME ZONE;

UPDATE recommendation_pairs
SET snapshot_version = 0
WHERE snapshot_version IS NULL;

ALTER TABLE recommendation_pairs
    ALTER COLUMN account_id SET NOT NULL;
ALTER TABLE recommendation_pairs
    ALTER COLUMN base_product_id SET NOT NULL;
ALTER TABLE recommendation_pairs
    ALTER COLUMN recommended_product_id SET NOT NULL;
ALTER TABLE recommendation_pairs
    ALTER COLUMN snapshot_version SET DEFAULT 0;
ALTER TABLE recommendation_pairs
    ALTER COLUMN snapshot_version SET NOT NULL;
ALTER TABLE recommendation_pairs
    ALTER COLUMN support_count SET NOT NULL;
ALTER TABLE recommendation_pairs
    ALTER COLUMN confidence SET NOT NULL;
ALTER TABLE recommendation_pairs
    ALTER COLUMN updated_at SET NOT NULL;

ALTER TABLE base_product_stats
    ADD COLUMN IF NOT EXISTS snapshot_version BIGINT;
ALTER TABLE base_product_stats
    ADD COLUMN IF NOT EXISTS docs_with_base BIGINT;

UPDATE base_product_stats
SET snapshot_version = 0
WHERE snapshot_version IS NULL;

ALTER TABLE base_product_stats
    ALTER COLUMN account_id SET NOT NULL;
ALTER TABLE base_product_stats
    ALTER COLUMN base_product_id SET NOT NULL;
ALTER TABLE base_product_stats
    ALTER COLUMN snapshot_version SET DEFAULT 0;
ALTER TABLE base_product_stats
    ALTER COLUMN snapshot_version SET NOT NULL;
ALTER TABLE base_product_stats
    ALTER COLUMN docs_with_base SET NOT NULL;

ALTER TABLE recommendation_pairs
    DROP CONSTRAINT IF EXISTS uq_recommendation_pairs_account_base_reco;

ALTER TABLE base_product_stats
    DROP CONSTRAINT IF EXISTS uq_base_product_stats_account_base;

DROP INDEX IF EXISTS uq_recommendation_pairs_account_base_reco;
DROP INDEX IF EXISTS idx_recommendation_pairs_account_base_support;
DROP INDEX IF EXISTS uq_base_product_stats_account_base;
DROP INDEX IF EXISTS idx_base_product_stats_account_base;

CREATE UNIQUE INDEX IF NOT EXISTS uq_account_installations_account_id
    ON account_installations (account_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_account_settings_account_id
    ON account_settings (account_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_recommendation_pairs_account_version_base_reco
    ON recommendation_pairs (account_id, snapshot_version, base_product_id, recommended_product_id);

CREATE INDEX IF NOT EXISTS idx_recommendation_pairs_account_version_base_support
    ON recommendation_pairs (account_id, snapshot_version, base_product_id, support_count DESC);

CREATE UNIQUE INDEX IF NOT EXISTS uq_base_product_stats_account_version_base
    ON base_product_stats (account_id, snapshot_version, base_product_id);

CREATE INDEX IF NOT EXISTS idx_base_product_stats_account_version_base
    ON base_product_stats (account_id, snapshot_version, base_product_id);
