-- Dev-only bootstrap of the outbox_event table.
--
-- WHY THIS EXISTS: no service creates outbox_event in the dev database until
-- the aws-ingestor adopts the outbox-starter (Day 11). But Debezium needs a
-- real table to tail TODAY, so we create it here for the dev stack.
--
-- Shape is identical to shared/outbox-starter's V1__create_outbox_event.sql.
-- CREATE ... IF NOT EXISTS means it coexists harmlessly with that Flyway
-- migration when the ingestor later runs against this same database.
--
-- NOTE: files in postgres-init/ run ONLY on a fresh data volume. If your
-- Postgres volume already exists, apply this once by hand (see Day 7 notes).

-- The /docker-entrypoint-initdb.d folder only runs once in a database's entire lifetime—specifically,
-- the very first time the container is created with an empty hard drive.
-- If you just run docker compose down and docker compose up -d, Postgres will see that it already has data on
-- its volume and will completely ignore this file. To get this file to run, you must either:
-- Destroy your database entirely using docker compose down -v (which wipes the volume clean) and restart it.
--  Or, simply open pgAdmin, paste this SQL text into the query tool, and run it manually.


CREATE TABLE IF NOT EXISTS public.outbox_event(
    id             UUID         PRIMARY KEY,
    aggregate_type VARCHAR(255) NOT NULL,
    aggregate_id   VARCHAR(255) NOT NULL,
    type           VARCHAR(255) NOT NULL,
    payload        JSONB        NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_outbox_event_created_at ON public.outbox_event(created_at);