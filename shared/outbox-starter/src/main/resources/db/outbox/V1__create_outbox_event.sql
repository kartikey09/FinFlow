-- The Transactional Outbox table.
--
-- Your app saves an event here at the exact same time it updates its main data.
-- Because both saves happen in one transaction, they either succeed together or
-- fail together (no data is lost or duplicated).
--
-- A background tool called Debezium will read this table
-- and safely copy these events over to Kafka. Your app never talks to Kafka directly!


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
-- Creates a Database Index on the created_at column.
-- Why it matters: Outbox tables grow massively huge very quickly. In the future, you will likely write a cleanup script that says,
-- "Delete all events older than 7 days." Without an index, Postgres would have to read every single row in the massive table to find the old ones.
-- With this index, it can find and delete them almost instantly.

COMMENT ON TABLE outbox_event IS
    'Transactional outbox: events written atomically with business state; Debezium tails these via CDC (Day 7).';

-- This is a Flyway migrations file. when a new person pull and runs this repo, the flyway checks the
-- flyway_schema_history to see it this DB was ever created. Flyway writes a row into its flyway_schema_history table
-- that essentially says: "On June 9, 2026, I successfully executed V2__create_outbox_event_table.sql. Do not run
-- it again."

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
-- This becomes the main body of the Kafka message. JSONB is a special PostgreSQL data type that stores
-- JSON text in a highly compressed, optimized binary format. This allows you to store any shape of
-- data without having to alter the table structure later.
