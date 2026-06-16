-- cost-normalizer baseline schema (schema: normalizer, via Flyway default-schema).
--
-- processed_event is the idempotency ledger and the consume-side counterpart to
-- the outbox: the outbox guarantees an event is EMITTED once; processed_event
-- guarantees it is HANDLED once. The consumer checks this table before handling
-- an event and inserts into it after — inside one transaction — so an
-- at-least-once redelivery of the same event id is detected and skipped.

CREATE TABLE IF NOT EXISTS processed_event (
    event_id     UUID         PRIMARY KEY,
    event_type   VARCHAR(255),
    processed_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
