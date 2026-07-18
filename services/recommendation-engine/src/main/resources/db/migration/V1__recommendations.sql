-- Day 26: the recommendation-engine's own schema.
--
-- FinFlow uses schema-per-service. commitment-tracker owns 'commitments',
-- query-api owns 'read_model', the adapters own '{aws,gcp}_adapter'. The
-- recommendation-engine owns 'recommendations'. It NEVER reaches into another
-- service's schema — it learns everything it needs from the two event streams
-- it consumes (finflow.events.commitment and finflow.events.cost-normalized).
-- That's what keeps this a real microservice and not a shared-database monolith.

CREATE SCHEMA IF NOT EXISTS recommendations;

-- =============================================================================
-- recommendation
--   One row per ACTIONABLE recommendation. The dashboard reads this (via
--   query-api's projection) sorted by estimated_savings_usd DESC.
--
--   IDEMPOTENCY / SUPERSEDING — the important design decision.
--   A commitment doesn't generate ONE underutilization alert; it generates a
--   fresh CommitmentUnderutilized every day the streak continues. If each alert
--   minted a new recommendation row, one idle commitment would produce a growing
--   pile of near-identical "rebalance me" cards. Useless in a dashboard.
--
--   So a recommendation is keyed by (tenant, commitment, type), and a newer
--   signal for the same key UPDATES the existing row (bumping the evidence and
--   the savings estimate) rather than inserting. The natural key is a UNIQUE
--   constraint; the consumer does INSERT ... ON CONFLICT DO UPDATE.
--
--   status lets a recommendation be retired without deleting history:
--     OPEN       - current and actionable
--     SUPERSEDED - a different type now applies to this commitment
--                  (e.g. SELL replaces REBALANCE once the streak passes 14 days)
-- =============================================================================
CREATE TABLE IF NOT EXISTS recommendations.recommendation (
    id                    UUID          PRIMARY KEY,
    tenant_id             VARCHAR(64)   NOT NULL,
    type                  VARCHAR(32)   NOT NULL,        -- 'REBALANCE' | 'PURCHASE_COMMITMENT' | 'SELL'
    commitment_id         VARCHAR(128),                  -- null for PURCHASE_COMMITMENT (there is no commitment yet)
    account_id            VARCHAR(64),
    vendor                VARCHAR(16),
    service               VARCHAR(128),                  -- the SKU dimension the rule matched on
    region                VARCHAR(64),
    estimated_savings_usd NUMERIC(20,6) NOT NULL,        -- the headline number; dashboard sorts by this
    evidence              JSONB         NOT NULL,         -- why we think so: the facts the rule fired on
    status                VARCHAR(16)   NOT NULL DEFAULT 'OPEN',   -- 'OPEN' | 'SUPERSEDED'
    -- the last event that touched this row, for exact-redelivery idempotency
    last_event_id         UUID,
    first_seen_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),

    -- The natural key that makes a repeated signal UPDATE instead of pile up.
    -- COALESCE isn't allowed in a table constraint, so PURCHASE_COMMITMENT (which
    -- has a null commitment_id) is keyed on (tenant, type, account, service)
    -- instead — handled by a partial unique index below rather than one blanket
    -- constraint, because the two recommendation shapes have different identities.
    CONSTRAINT uq_reco_commitment UNIQUE (tenant_id, type, commitment_id)
);

-- PURCHASE_COMMITMENT has no commitment_id (you're recommending they BUY one), so
-- its identity is the on-demand (account, service) pair that's bleeding money.
-- A partial unique index gives that its own dedup key without colliding with the
-- commitment-keyed constraint above (where commitment_id IS NOT NULL).
CREATE UNIQUE INDEX IF NOT EXISTS uq_reco_purchase
    ON recommendations.recommendation (tenant_id, type, account_id, service)
    WHERE commitment_id IS NULL;

-- The dashboard's "top recommendations" query: ORDER BY estimated_savings_usd DESC.
CREATE INDEX IF NOT EXISTS idx_reco_savings
    ON recommendations.recommendation (status, estimated_savings_usd DESC);

COMMENT ON TABLE recommendations.recommendation IS
    'Actionable FinOps recommendations (Day 26). Keyed so repeated signals update in place rather than duplicating; emitted to finflow.events.recommendation via the outbox.';

-- =============================================================================
-- ondemand_spend_rollup
--   The engine's OWN running tally of on-demand spend per (account, service).
--   PURCHASE_COMMITMENT needs "sustained on-demand spend past break-even" — but a
--   single CostLineItemNormalized event is one line item, not a trend. The engine
--   can't ask cost-normalizer's DB (schema-per-service), so it accumulates its own
--   rollup from the cost stream as events arrive. This is a projection, same
--   pattern as query-api's mv_* tables.
--
--   "On-demand" = a cost line with NO commitment_id. That's the spend a commitment
--   could have covered.
-- =============================================================================
CREATE TABLE IF NOT EXISTS recommendations.ondemand_spend_rollup (
    tenant_id          VARCHAR(64)   NOT NULL,
    account_id         VARCHAR(64)   NOT NULL,
    vendor             VARCHAR(16),
    service            VARCHAR(128)  NOT NULL,
    region             VARCHAR(64),
    observed_days      INT           NOT NULL DEFAULT 0,   -- distinct usage-days seen (proxy for "sustained")
    total_ondemand_usd NUMERIC(20,6) NOT NULL DEFAULT 0,
    last_usage_day     VARCHAR(64),
    last_event_id      UUID,
    updated_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, account_id, service)
);

COMMENT ON TABLE recommendations.ondemand_spend_rollup IS
    'Engine-local projection of on-demand (uncommitted) spend per account+service, accumulated from the cost stream to drive PURCHASE_COMMITMENT.';
