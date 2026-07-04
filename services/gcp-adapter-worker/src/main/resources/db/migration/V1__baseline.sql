-- Day 19: gcp-adapter-worker dedup table.
--
-- Identical shape to aws_adapter.processed_command (Day 18) in a separate schema.
-- Both adapters keep their own ledgers on purpose: it makes the "what has THIS
-- adapter already handled?" query a schema-local lookup with no coupling
-- across services. The PK is idempotency_key, deterministic from the
-- orchestrator as (sagaId + step + direction).

CREATE SCHEMA IF NOT EXISTS gcp_adapter;

CREATE TABLE IF NOT EXISTS gcp_adapter.processed_command (
    idempotency_key   VARCHAR(255)  PRIMARY KEY,
    saga_id           UUID          NOT NULL,
    step              VARCHAR(64)   NOT NULL,
    direction         VARCHAR(8)    NOT NULL,           -- 'DO' | 'UNDO'
    outcome           VARCHAR(16)   NOT NULL,           -- 'SUCCESS' | 'FAILURE'
    reason            VARCHAR(512),
    processed_at      TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_gcp_processed_command_saga
    ON gcp_adapter.processed_command (saga_id);

COMMENT ON TABLE gcp_adapter.processed_command IS
    'GCP adapter idempotency ledger. Same shape as aws_adapter.processed_command.';
