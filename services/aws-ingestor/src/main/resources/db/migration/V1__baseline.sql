-- Baseline schema for the aws-ingestor.
--
-- The ingestor persists a poll cursor per billing source so polling is
-- RESUMABLE and IDEMPOTENT: on restart it continues from the last token it
-- fully processed, rather than re-reading from the beginning. The Day-11 poll
-- loop fills this in; Day 5 just proves the table + JPA + Flyway all work.

CREATE SCHEMA IF NOT EXISTS ingestion;

CREATE TABLE IF NOT EXISTS ingestion.poll_cursor (
    source      VARCHAR(64)  PRIMARY KEY,
    last_token  VARCHAR(255),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

COMMENT ON TABLE ingestion.poll_cursor IS
    'Resumable poll position per billing source. Filled by the poll loop on Day 11.';
