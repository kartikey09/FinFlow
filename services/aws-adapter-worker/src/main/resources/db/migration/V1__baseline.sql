-- Day 18: the aws-adapter-worker's dedup table.
--
-- One responsibility: catch a redelivered Kafka command whose Chaos API side
-- effect has already been applied, so we don't call the endpoint twice AND
-- (crucially) so we still re-publish the cached result event.
--
-- The PK is idempotency_key, which the orchestrator sets deterministically as
-- (sagaId + step + direction). See saga.SagaCommandPayload.keyFor.

CREATE SCHEMA IF NOT EXISTS aws_adapter;

CREATE TABLE IF NOT EXISTS aws_adapter.processed_command (
    idempotency_key   VARCHAR(255)  PRIMARY KEY,
    saga_id           UUID          NOT NULL,
    step              VARCHAR(64)   NOT NULL,
    direction         VARCHAR(8)    NOT NULL,           -- 'DO' | 'UNDO'
    outcome           VARCHAR(16)   NOT NULL,           -- 'SUCCESS' | 'FAILURE'
    reason            VARCHAR(512),                     -- populated on failure
    processed_at      TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_processed_command_saga
    ON aws_adapter.processed_command (saga_id);

COMMENT ON TABLE aws_adapter.processed_command IS
    'Idempotency ledger: one row per command the adapter has already applied its side effect for.';
