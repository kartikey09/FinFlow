-- Day 20: add the vendor column to saga_instances.
--
-- Nullable so that any Day-17/18/19 rows already in the DB survive the migration
-- without a backfill dance. New sagas from Day 20 onward will always have a
-- value (RebalanceRequest requires it and the service will reject a blank).

ALTER TABLE saga.saga_instances
    ADD COLUMN IF NOT EXISTS vendor VARCHAR(16);

COMMENT ON COLUMN saga.saga_instances.vendor IS
    'Which vendor adapter handles this saga (AWS or GCP). NULL for pre-Day-20 rows.';
