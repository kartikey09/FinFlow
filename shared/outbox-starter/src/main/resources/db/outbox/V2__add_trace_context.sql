-- Day 24: carry the distributed-trace context THROUGH the outbox.
--
-- WHY THIS COLUMN EXISTS (the whole reason Day 24 is hard)
--
-- The build plan says "your producer must inject traceparent into Kafka headers".
-- But FinFlow has NO Kafka producer for domain events. The app writes a row here;
-- Debezium — a completely separate JVM tailing the WAL — is what publishes to
-- Kafka. Debezium has no idea a trace was in flight when the row was written.
--
-- So the trace context dies at the database boundary unless we PERSIST it:
--
--   app span active
--     -> INSERT INTO outbox_event (..., trace_parent)   <-- W3C traceparent, same tx
--        -> Debezium reads the WAL row
--           -> EventRouter SMT copies trace_parent -> Kafka header "traceparent"
--              -> consumer's Spring Kafka observation extracts it (W3C propagator)
--                 -> child span. ONE TRACE.
--
-- A nice consequence: because the traceparent is a column in the same row as the
-- event, it commits atomically with the business change. If the transaction rolls
-- back, the trace context rolls back with it. You can never have a span pointing
-- at an event that was never written — which is a guarantee a direct Kafka
-- producer could NOT give you.
--
-- Format: W3C Trace Context "traceparent" header.
--   00-<32-hex trace-id>-<16-hex span-id>-<2-hex flags>   = 55 chars.
-- VARCHAR(64) leaves headroom. NULLABLE on purpose: an event appended outside any
-- active span (a retention sweep, a test, a service with tracing switched off) is
-- still a perfectly valid event — it just starts a fresh trace downstream.

ALTER TABLE outbox_event
    ADD COLUMN IF NOT EXISTS trace_parent VARCHAR(64);

COMMENT ON COLUMN outbox_event.trace_parent IS
    'W3C traceparent captured at append time. Debezium EventRouter maps this to the Kafka "traceparent" header so consumers continue the same trace. Null = event appended with no active span.';
