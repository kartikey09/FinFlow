package io.finflow.common;

/**
 * Canonical Kafka topic names, shared so producers and consumers never drift on
 * a string literal. Centralizing them is the cheapest possible guard against a
 * typo silently routing events into the void.
 */
public final class Topics {

    private Topics() {}

    // -------------------------------------------------------------------------
    // CURRENT (Week 3+) — these match what Debezium's EventRouter actually emits
    // -------------------------------------------------------------------------

    /** Raw, vendor-shaped billing events from both ingestors (Day 11/12). */
    public static final String RAW_BILLING = "finflow.events.billing";

    /** Normalized, canonical cost events from the cost-normalizer (Day 13). */
    public static final String COST_NORMALIZED = "finflow.events.cost-normalized";

    /**
     * Commitment alert events from the commitment-tracker (Day 14): the three
     * event types CommitmentUnderutilized / CommitmentSaturated /
     * CommitmentExpiringSoon all route here, distinguished by the
     * {@code eventType} header (set by the Outbox Event Router).
     */
    public static final String COMMITMENT_EVENTS = "finflow.events.commitment";

    /** Dev-only round-trip topic used by the Week 1 smoke test. */
    public static final String SMOKE = "finflow.smoke";

    // -------------------------------------------------------------------------
    // LEGACY — kept as constants so older code doesn't break during cleanup.
    // The build plan replaced these with finflow.events.* (Week 3 pivot).
    // -------------------------------------------------------------------------

    /** @deprecated use {@link #RAW_BILLING}. */
    @Deprecated public static final String RAW_BILLING_AWS = "finflow.billing.raw.aws";
    /** @deprecated use {@link #RAW_BILLING}. */
    @Deprecated public static final String RAW_BILLING_GCP = "finflow.billing.raw.gcp";
    /** @deprecated use {@link #COST_NORMALIZED}. */
    @Deprecated public static final String CANONICAL_COST  = "finflow.cost.canonical";
}
