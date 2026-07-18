-- Day 26: query-api projection for recommendations.
--
-- The recommendation-engine owns the authoritative recommendations.recommendation
-- table. query-api keeps its OWN read-model copy, fed from finflow.events.recommendation,
-- so the dashboard's read path stays a single service (query-api) rather than fanning
-- the UI out across service databases. Same CQRS pattern as mv_commitment_health.

CREATE TABLE IF NOT EXISTS read_model.mv_recommendation (
    id                    UUID          PRIMARY KEY,
    tenant_id             VARCHAR(64)   NOT NULL,
    type                  VARCHAR(32)   NOT NULL,
    commitment_id         VARCHAR(128),
    account_id            VARCHAR(64),
    vendor                VARCHAR(16),
    service               VARCHAR(128),
    region                VARCHAR(64),
    estimated_savings_usd NUMERIC(20,6) NOT NULL,
    evidence              JSONB,
    updated_at            TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_mv_recommendation_savings
    ON read_model.mv_recommendation (estimated_savings_usd DESC);

COMMENT ON TABLE read_model.mv_recommendation IS
    'Projection: recommendations from finflow.events.recommendation, maintained by RecommendationEventConsumer; backs GET /api/v1/recommendations.';
