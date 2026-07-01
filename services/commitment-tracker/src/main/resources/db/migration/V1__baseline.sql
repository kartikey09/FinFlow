-- Day 14: the commitment-tracker's three state tables.
--
-- Three tables, three purposes:
--   commitments         master list of all reservations/savings-plans/CUDs (seeded)
--   commitment_utilization     rolling per-(commitment, day) used/available counter
--   threshold_streaks   per-commitment consecutive-low-day tracker for alerting
--
-- All in the `commitments` schema, set by hibernate.default_schema. The shared
-- outbox lives in `public` and is pinned there via orm.xml.

CREATE SCHEMA IF NOT EXISTS commitments;

-- =============================================================================
-- commitments — master list. Vendor-agnostic representation of an RI / SP / CUD.
-- Seeded once; in a real system would be reconciled against vendor APIs.
-- =============================================================================
CREATE TABLE IF NOT EXISTS commitments.commitments (
    commitment_id      VARCHAR(128)  PRIMARY KEY,
    vendor             VARCHAR(16)   NOT NULL,             -- 'AWS' | 'GCP'
    type               VARCHAR(32)   NOT NULL,             -- 'RI' | 'SAVINGS_PLAN' | 'CUD'
    account_id         VARCHAR(64),
    service            VARCHAR(128),
    region             VARCHAR(64),
    daily_amount_usd   NUMERIC(20,6) NOT NULL,             -- the daily commitment ceiling in USD
    start_at           TIMESTAMPTZ   NOT NULL,
    expires_at         TIMESTAMPTZ   NOT NULL,
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- Demo seeds — these commitment ids match what the chaos-api synthetic data
-- references (the AWS reservation ARNs and the GCP CUD credit names). Without
-- these seeds the tracker would see commitment_ids on events it can't recognize
-- and silently skip them.
INSERT INTO commitments.commitments
    (commitment_id, vendor, type, account_id, service, region, daily_amount_usd, start_at, expires_at)
VALUES
    ('arn:aws:ec2:us-east-1:111122223333:ri/abc',     'AWS', 'RI',           '111122223333', 'AmazonEC2', 'us-east-1', 100.00,
        now() - interval '60 days', now() + interval '305 days'),
    ('arn:aws:ec2:us-east-1:444455556666:ri/xyz',     'AWS', 'RI',           '444455556666', 'AmazonEC2', 'us-east-1',  50.00,
        now() - interval '90 days', now() + interval '15 days'),    -- expires within 30d window
    ('arn:aws:savingsplans::777788889999:plan/sp-1',  'AWS', 'SAVINGS_PLAN', '777788889999', NULL,        NULL,         200.00,
        now() - interval '30 days', now() + interval '335 days'),
    ('cud-2025-11',                                    'GCP', 'CUD',          '0123AB-CDEF45-6789GH', 'compute', NULL,  75.00,
        now() - interval '15 days', now() + interval '350 days')
ON CONFLICT (commitment_id) DO NOTHING;

-- =============================================================================
-- commitment_utilization — rolling time series of (commitment, day) -> used vs available.
-- One row per commitment per day; updated incrementally as cost events arrive.
-- =============================================================================
CREATE TABLE IF NOT EXISTS commitments.commitment_utilization (
    commitment_id    VARCHAR(128)  NOT NULL,
    period_day       DATE          NOT NULL,                -- UTC day extracted from event.usageStart
    used_amount_usd  NUMERIC(20,6) NOT NULL DEFAULT 0,      -- sum of cost_usd attributed to this commitment
    available_usd    NUMERIC(20,6) NOT NULL,                -- daily ceiling at the time of first write
    last_event_id    UUID,
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    PRIMARY KEY (commitment_id, period_day),
    CONSTRAINT fk_util_commitment
        FOREIGN KEY (commitment_id) REFERENCES commitments.commitments(commitment_id)
);

CREATE INDEX IF NOT EXISTS idx_util_period_day  ON commitments.commitment_utilization (period_day);

-- =============================================================================
-- threshold_streaks — one row per commitment, tracks consecutive low-utilization days.
-- Updated as each new day's row crosses the threshold; reset when any day rebounds.
-- The "for 3+ consecutive days" rule fires when current_streak crosses streak-days
-- AND we have not already emitted for that streak (last_emitted_streak_end_day differs).
-- =============================================================================
CREATE TABLE IF NOT EXISTS commitments.threshold_streaks (
    commitment_id              VARCHAR(128)  PRIMARY KEY,
    rule                       VARCHAR(32)   NOT NULL DEFAULT 'UNDERUTILIZATION',
    current_streak_days        INT           NOT NULL DEFAULT 0,
    streak_start_day           DATE,
    last_observed_day          DATE,
    last_emitted_streak_end_day DATE,                       -- prevents repeat alerts for the same streak
    updated_at                 TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT fk_streak_commitment
        FOREIGN KEY (commitment_id) REFERENCES commitments.commitments(commitment_id)
);

COMMENT ON TABLE commitments.commitment_utilization IS
    'Per-commitment per-day used/available; updated incrementally as cost events arrive.';
COMMENT ON TABLE commitments.threshold_streaks IS
    'Consecutive low-utilization days per commitment; used to fire "under X% for N+ days" alerts.';
