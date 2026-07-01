-- Day 13: the cost-normalizer's real schema.
--
-- accounts: small metadata table mapping a vendor account/billing-account id to
-- the team that owns it. Source of "enrichment" data — joined into every
-- normalized line item so downstream queries (Day 15 dashboard) can group by
-- team without re-deriving it. In a real system this would be backed by an
-- HR/ownership service; for the MVP we seed a couple of demo entries here and
-- the normalizer drops back to "unassigned" if the lookup misses.
CREATE TABLE IF NOT EXISTS accounts (
    account_id   VARCHAR(64)  PRIMARY KEY,
    vendor       VARCHAR(16)  NOT NULL,
    team         VARCHAR(64)  NOT NULL,
    cost_center  VARCHAR(64)
);

-- Demo seeds matching the chaos-api synthetic data.
INSERT INTO accounts(account_id, vendor, team, cost_center) VALUES
    ('111122223333',         'AWS', 'platform',  'CC-100'),
    ('444455556666',         'AWS', 'data',      'CC-200'),
    ('777788889999',         'AWS', 'product',   'CC-300'),
    ('0123AB-CDEF45-6789GH', 'GCP', 'platform',  'CC-100'),
    ('98ZYXW-VUTSR4-3210QP', 'GCP', 'data',      'CC-200')
ON CONFLICT (account_id) DO NOTHING;

-- cost_ledger: one row per normalized line item — the canonical, vendor-agnostic
-- spend record. Day 13's KEY DELIVERABLE.
--
-- DEDUP STRATEGY (per the plan):
--   L1 — Caffeine cache (last 100k event ids) in memory in front of the DB.
--   L2 — UNIQUE(tenant_id, event_id) here as the hard guarantee. On a unique
--        violation the consumer logs and drops the duplicate (doesn't fail), so
--        an at-least-once redelivery cannot create a duplicate ledger row even
--        if the L1 cache missed.
CREATE TABLE IF NOT EXISTS cost_ledger (
    id                BIGSERIAL    PRIMARY KEY,
    tenant_id         VARCHAR(64)  NOT NULL,
    event_id          UUID         NOT NULL,
    vendor            VARCHAR(16)  NOT NULL,        -- 'AWS' | 'GCP'
    account_id        VARCHAR(64),
    team              VARCHAR(64)  NOT NULL,        -- enriched (fallback 'unassigned')
    service           VARCHAR(128),                 -- 'AmazonEC2' / GCP service id
    resource_id       VARCHAR(256),
    region            VARCHAR(64),
    usage_start       VARCHAR(64),                  -- ISO-8601 strings (vendor formats vary)
    usage_end         VARCHAR(64),
    usage_amount      NUMERIC(20,6),
    cost_usd          NUMERIC(20,6) NOT NULL,
    list_price_usd    NUMERIC(20,6),                -- pre-discount price (committed-usage-aware for GCP)
    commitment_id     VARCHAR(128),                 -- reservation/CUD identifier; null = on-demand
    line_item_type    VARCHAR(64),                  -- 'Usage' | 'DiscountedUsage' | 'regular' | ...
    raw_source        VARCHAR(16)  NOT NULL,        -- which ingestor produced the source event
    normalized_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_cost_ledger_tenant_event UNIQUE (tenant_id, event_id)
);

CREATE INDEX IF NOT EXISTS idx_cost_ledger_team_day    ON cost_ledger (team, usage_start);
CREATE INDEX IF NOT EXISTS idx_cost_ledger_commitment  ON cost_ledger (commitment_id);
CREATE INDEX IF NOT EXISTS idx_cost_ledger_vendor      ON cost_ledger (vendor);

COMMENT ON TABLE cost_ledger IS
    'Canonical normalized spend record. One row per RawCostLineItem event. UNIQUE(tenant_id,event_id) is the hard idempotency guarantee (L2). Day 15 reads from here.';
