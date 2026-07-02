-- Day 15 read model: the two "materialized views" the plan calls for.
--
-- IMPLEMENTATION NOTE, IMPORTANT
-- ------------------------------
-- The plan names these "materialized views" and prefixes them mv_*. Postgres
-- MATERIALIZED VIEW objects need a REFRESH MATERIALIZED VIEW (scheduled or
-- triggered) and do not update themselves on inserts. For an event-driven
-- read side, the CQRS-correct implementation is PLAIN TABLES that the consumer
-- writes into as events arrive — incrementally, with no periodic refresh.
--
-- I honor the mv_* naming so the plan's language stays intact, but these are
-- projection tables the consumer maintains directly. If you ever want strict
-- Postgres MVs later (e.g. for ad-hoc analytics decoupled from the stream),
-- add a real MV alongside; nothing here blocks that.

CREATE SCHEMA IF NOT EXISTS read_model;

-- =============================================================================
-- mv_spend_by_team_day
--   The dashboard's bar-chart data source. Pre-aggregated so /spend/by-team is
--   a single SELECT with no runtime SUM. Updated event-by-event: for each
--   CostLineItemNormalized, look up (team, day) and add cost_usd to the total.
--   Also stores last_event_id for in-row idempotency (same trick as Day 14).
-- =============================================================================
CREATE TABLE IF NOT EXISTS read_model.mv_spend_by_team_day (
    tenant_id      VARCHAR(64)  NOT NULL,
    team           VARCHAR(64)  NOT NULL,
    period_day     DATE         NOT NULL,
    cost_usd       NUMERIC(20,6) NOT NULL DEFAULT 0,
    event_count    INT          NOT NULL DEFAULT 0,
    last_event_id  UUID,
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, team, period_day)
);

CREATE INDEX IF NOT EXISTS idx_mv_spend_by_period
    ON read_model.mv_spend_by_team_day (period_day);

-- =============================================================================
-- mv_commitment_health
--   The dashboard's utilization table. One row per commitment carrying the
--   headline: latest observed utilization fraction, plus the latest alert
--   status (populated from finflow.events.commitment). No time series here —
--   the consumer's job is to keep this ONE row current per commitment.
-- =============================================================================
CREATE TABLE IF NOT EXISTS read_model.mv_commitment_health (
    tenant_id                VARCHAR(64)   NOT NULL,
    commitment_id            VARCHAR(128)  NOT NULL,
    vendor                   VARCHAR(16),
    account_id               VARCHAR(64),
    latest_period_day        DATE,
    latest_used_usd          NUMERIC(20,6),
    latest_available_usd     NUMERIC(20,6),
    latest_fraction          NUMERIC(10,6),
    status                   VARCHAR(32)   NOT NULL DEFAULT 'HEALTHY',
                             -- 'HEALTHY' | 'UNDERUTILIZED' | 'SATURATED' | 'EXPIRING_SOON'
    last_alert_type          VARCHAR(64),
    last_alert_at            TIMESTAMPTZ,
    last_alert_event_id      UUID,
    updated_at               TIMESTAMPTZ   NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, commitment_id)
);

COMMENT ON TABLE read_model.mv_spend_by_team_day IS
    'Projection: pre-aggregated (team, day) spend, maintained by CostNormalizedEventConsumer.';
COMMENT ON TABLE read_model.mv_commitment_health IS
    'Projection: latest utilization fraction + alert status per commitment; maintained by CommitmentEventConsumer.';
