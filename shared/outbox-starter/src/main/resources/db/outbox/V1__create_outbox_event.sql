-- The transactional outbox table.
--
-- Services write a row here in the SAME transaction as their business change,
-- so the event and the state change commit (or roll back) atomically. Day 7's
-- Debezium connector tails Postgres' write-ahead log, sees new rows here, and
-- publishes them to Kafka — the service itself never touches Kafka.
--
-- Column design matches Debezium's Outbox Event Router conventions (Day 7 maps
-- these via the connector config):
--   aggregate_type -> routes the event to a Kafka topic
--   aggregate_id   -> becomes the Kafka message key (preserves per-aggregate ordering)
--   type           -> the event type, carried as a header
--   payload        -> the event body (jsonb -> Kafka message value)

CREATE TABLE IF NOT EXISTS outbox_event (
    id             UUID         PRIMARY KEY,
    aggregate_type VARCHAR(255) NOT NULL,
    aggregate_id   VARCHAR(255) NOT NULL,
    type           VARCHAR(255) NOT NULL,
    payload        JSONB        NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Helps any retention/cleanup job and keeps WAL-replay ordering observable.
CREATE INDEX IF NOT EXISTS idx_outbox_event_created_at ON outbox_event (created_at);

COMMENT ON TABLE outbox_event IS
    'Transactional outbox: events written atomically with business state; Debezium tails these via CDC (Day 7).';


-- The columns in this table are not arbitrary; they are strictly designed to match the expectations of Debezium's
-- "Outbox Event Router":
--
-- id (UUID): A unique identifier for the event. Downstream consumers use this to detect duplicates (Idempotency).
--
-- aggregate_type: Debezium reads this column to figure out which Kafka Topic to send the message to
-- (e.g., billing sends it to the billing topic).
--
-- aggregate_id: Debezium uses this as the Kafka Message Key. If you have five events for the same aggregate_id,
-- Kafka guarantees they will be processed in the exact order they were created.
--
-- type: A label describing what happened (e.g., RawBillingPagePulled). Debezium attaches this to the Kafka message
-- as a header so consumers know what kind of event they are receiving before parsing the body.
--
-- payload (JSONB): The actual meat of the event (e.g., the raw billing data or the URL to the S3 bucket).
-- This becomes the main body of the Kafka message.