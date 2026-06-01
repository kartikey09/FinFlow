-- =============================================================================
-- 00-init.sql — Runs ONCE when Postgres starts up against an empty data dir.
--
-- Postgres' entrypoint script processes files in /docker-entrypoint-initdb.d
-- alphabetically. The "00-" prefix ensures this runs first if we add more.
--
-- IMPORTANT: this only runs on FIRST startup. If you change this file later,
-- you need to wipe the volume (`docker compose down -v`) for it to re-run.
-- =============================================================================

-- Useful extensions
CREATE EXTENSION IF NOT EXISTS pgcrypto;        -- for gen_random_uuid()
CREATE EXTENSION IF NOT EXISTS pg_stat_statements;  -- query performance insight

-- Per-service schemas. We use one DB with multiple schemas in dev to keep
-- the compose file simple. Production would use one DB per service.
CREATE SCHEMA IF NOT EXISTS chaos_api;
CREATE SCHEMA IF NOT EXISTS aws_ingestor;
CREATE SCHEMA IF NOT EXISTS gcp_ingestor;
CREATE SCHEMA IF NOT EXISTS cost_normalizer;
CREATE SCHEMA IF NOT EXISTS commitment_tracker;
CREATE SCHEMA IF NOT EXISTS recommendation_engine;
CREATE SCHEMA IF NOT EXISTS saga_orchestrator;
CREATE SCHEMA IF NOT EXISTS aws_adapter_worker;
CREATE SCHEMA IF NOT EXISTS gcp_adapter_worker;
CREATE SCHEMA IF NOT EXISTS query_api;

-- Empty publication that Debezium will attach to in week 2.
-- We create it now so we don't forget; service migrations will ALTER
-- this publication to add their outbox tables.
DROP PUBLICATION IF EXISTS dbz_finflow_outbox;
CREATE PUBLICATION dbz_finflow_outbox;

-- Sanity message in the logs so we know this ran
DO $$
BEGIN
  RAISE NOTICE '✓ FinFlow init script complete · schemas + publication ready';
END $$;
