-- The Transactional Outbox table.
--
-- Your app saves an event here at the exact same time it updates its main data.
-- Because both saves happen in one transaction, they either succeed together or
-- fail together (no data is lost or duplicated).
--
-- A background tool called Debezium will read this table
-- and safely copy these events over to Kafka. Your app never talks to Kafka directly!
--
-- The columns are named specifically for Debezium to understand:
--   aggregate_type -> Tells Debezium which Kafka topic to send the event to.
--   aggregate_id   -> Used as the Kafka key to keep related events in the correct order.
--          Stores the specific ID of the object that changed (e.g., the specific AWS account ID or user ID).
--          When Debezium sends this to Kafka, Kafka uses this ID as a "Message Key" to guarantee that all
--          updates belonging to the same ID are processed in the exact order they were created.
--   type           -> A label describing the event (e.g., "InvoiceCreated").
--   payload        -> The actual JSON data of the event. - JSONB is a special PostgreSQL data type that stores
--           JSON text in a highly compressed, optimized binary format. This allows you to store any shape of
--           data without having to alter the table structure later.

CREATE TABLE IF NOT EXISTS outbox_event(
    id             UUID         PRIMARY KEY,
    aggregate_type VARCHAR(255) NOT NULL,
    aggregate_id   VARCHAR(255) NOT NULL,
    type           VARCHAR(255) NOT NULL,
    payload        JSONB        NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()  --Creates a timestamp column that includes the Timezone (TIMESTAMPTZ)
--DEFAULT now() - Java code doesn't even have to provide a time; the db automatically stamps it with exact micro sec the row was saved.
);

CREATE INDEX IF NOT EXISTS idx_outbox_event_created_at ON outbox_event (created_at);
-- Creates a Database Index on the created_at column.
-- Why it matters: Outbox tables grow massively huge very quickly. In the future, you will likely write a cleanup script that says,
-- "Delete all events older than 7 days." Without an index, Postgres would have to read every single row in the massive table to find the old ones.
-- With this index, it can find and delete them almost instantly.

--DB Metadata
COMMENT ON TABLE outbox_event IS
 'Transactional outbox: events written atomically with business state; Debezium tails these via CDC.';