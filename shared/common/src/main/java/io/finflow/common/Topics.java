package io.finflow.common;

/**
 * Canonical Kafka topic names, shared so producers and consumers never drift
 * on a string literal. Centralizing them here is the cheapest possible guard
 * against a typo silently routing events into the void.
 */
public final class Topics {

    private Topics() {}

    /** Raw, vendor-shaped billing events emitted by the ingestors (Week 3). */
    public static final String RAW_BILLING_AWS = "finflow.billing.raw.aws";
    public static final String RAW_BILLING_GCP = "finflow.billing.raw.gcp";

    /** Normalized, canonical cost events from the cost-normalizer (Week 3). */
    public static final String CANONICAL_COST = "finflow.cost.canonical";

    /** Dev-only round-trip topic used by the Week 1 smoke test. */
    public static final String SMOKE = "finflow.smoke";
}
