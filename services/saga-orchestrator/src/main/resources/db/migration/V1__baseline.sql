-- Day 16: the saga-orchestrator's single table.
--
-- One row per saga in flight. The design intent — everything a resuming
-- orchestrator needs after a crash lives in this row — is why the columns look
-- the way they do:
--
--   current_state    : where the saga IS right now (used by evaluate)
--   completed_steps  : what it DID (used by compensation to know what to undo)
--   version          : the optimistic lock (used by JPA to fail concurrent writes)
--   correlation_id   : the human/tracing handle
--
-- Deliberately NO separate events/commands table. Kafka is the log of events;
-- the outbox will be the log of commands (wired on Day 17). Both live in
-- their own well-established homes; this table is only saga STATE.

CREATE SCHEMA IF NOT EXISTS saga;

CREATE TABLE IF NOT EXISTS saga.saga_instances (
    id               UUID          PRIMARY KEY,
    type             VARCHAR(32)   NOT NULL,
    current_state    VARCHAR(32)   NOT NULL,
    completed_steps  JSONB         NOT NULL DEFAULT '[]'::jsonb,
    correlation_id   VARCHAR(128)  NOT NULL,
    version          BIGINT        NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- Non-terminal sagas are the only ones the orchestrator polls for on startup
-- (resume-in-flight logic will live on Day 17). Index by state so that scan
-- is a cheap seek even at 100k historical rows.
CREATE INDEX IF NOT EXISTS idx_saga_instances_current_state
    ON saga.saga_instances (current_state);

CREATE INDEX IF NOT EXISTS idx_saga_instances_correlation
    ON saga.saga_instances (correlation_id);

COMMENT ON TABLE saga.saga_instances IS
    'One row per saga in flight. Contains the entire recovery story: current_state + completed_steps + version.';
