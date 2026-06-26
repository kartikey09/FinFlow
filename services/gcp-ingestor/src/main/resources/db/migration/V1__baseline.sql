-- Baseline schema for the gcp-ingestor.
--
-- Lives in its OWN schema, gcp_ingestion: aws-ingestor owns `ingestion`, and two
-- services can't share one Flyway-managed schema (their schema histories would
-- collide). Creates the poll cursor (resumable polling) and the raw landing
-- table in one migration, since this service has no prior history.

CREATE SCHEMA IF NOT EXISTS gcp_ingestion;

CREATE TABLE IF NOT EXISTS gcp_ingestion.poll_cursor (
                                                         source      VARCHAR(64)  PRIMARY KEY,
    last_token  VARCHAR(255),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
    );

-- Raw GCP billing-export rows. row_key is a deterministic hash of the
-- identifying fields (GCP export rows, unlike AWS CUR, have no natural unique id)
-- and serves as the idempotency gate. cost_usd and committed_usage_discount are
-- the GCP-specific derived values (FX conversion + credits[] walk).
CREATE TABLE IF NOT EXISTS gcp_ingestion.gcp_cost_line_items_raw (
                                                                     row_key                   VARCHAR(64)   PRIMARY KEY,
    billing_account_id        VARCHAR(64),
    service_id                VARCHAR(64),
    service_description       VARCHAR(255),
    sku_id                    VARCHAR(64),
    project_id                VARCHAR(64),
    usage_start_time          VARCHAR(64),
    cost                      NUMERIC(20, 10),
    currency                  VARCHAR(8),
    currency_conversion_rate  NUMERIC(20, 10),
    cost_usd                  NUMERIC(20, 10),
    committed_usage_discount  NUMERIC(20, 10),
    cost_type                 VARCHAR(64),
    ingested_at               TIMESTAMPTZ   NOT NULL DEFAULT now()
    );

CREATE INDEX IF NOT EXISTS idx_gcp_raw_billing_account
    ON gcp_ingestion.gcp_cost_line_items_raw (billing_account_id);

COMMENT ON TABLE gcp_ingestion.gcp_cost_line_items_raw IS
    'Raw GCP billing-export rows; row_key is a deterministic hash used as the idempotency gate.';
