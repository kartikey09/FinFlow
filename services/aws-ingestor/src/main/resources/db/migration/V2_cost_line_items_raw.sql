-- The aws-ingestor's raw landing table for AWS CUR line items.
--
-- Every line item the poll loop pulls is written here BEFORE (in the same
-- transaction as) its outbox event. Two reasons:
--   1. Durability: a faithful record of exactly what we ingested, queryable for
--      debugging/reconciliation, independent of whatever the normalizer derives.
--   2. Idempotency gate: line_item_id is the PRIMARY KEY. The poll loop skips an
--      item that already exists here, so re-polling a page (e.g. after the
--      synthetic dataset's nextToken loops back to the start) never produces a
--      duplicate event. This is the producer-side half of exactly-once; the
--      consumer's processed_event ledger is the other half.
--
-- Schema-qualified to `ingestion` so it lands there regardless of Flyway's
-- default schema. (The shared outbox table lives in `public` — see orm.xml.)

CREATE TABLE IF NOT EXISTS ingestion.cost_line_items_raw (
                                                             line_item_id      VARCHAR(128)   PRIMARY KEY,
    payer_account_id  VARCHAR(32),
    usage_account_id  VARCHAR(32),
    product_code      VARCHAR(64),
    usage_type        VARCHAR(128),
    unblended_cost    NUMERIC(20, 10),
    usage_start_date  VARCHAR(64),
    usage_end_date    VARCHAR(64),
    ingested_at       TIMESTAMPTZ    NOT NULL DEFAULT now()
    );

-- Most downstream queries group by the account that incurred the usage.
CREATE INDEX IF NOT EXISTS idx_cost_line_items_raw_usage_account
    ON ingestion.cost_line_items_raw (usage_account_id);

COMMENT ON TABLE ingestion.cost_line_items_raw IS
    'Raw AWS CUR line items as pulled from the Chaos API; line_item_id is the idempotency gate.';
