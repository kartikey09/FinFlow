package io.finflow.normalizer.normalize;

import java.util.UUID;

/**
 * The canonical, vendor-agnostic spend record.
 *
 * <p><b>The whole point of Day 13 lives in this shape.</b> Two completely
 * different vendor formats (AWS CUR, flat with slashed keys; GCP billing export,
 * nested objects plus arrays) collapse into <i>one</i> record here. Everything
 * downstream — the commitment-tracker on Day 14, the query-api / dashboard on
 * Day 15 — depends only on this type. Adding a third vendor later means writing
 * a third {@code Normalizer} and updating dispatch; downstream stays unchanged.
 *
 * <p>The fields exactly match the canonical schema in the build plan.
 *
 * <p>Field notes:
 * <ul>
 *   <li>{@code eventId} is the SOURCE outbox event id (from the Kafka header) —
 *       it lets downstream consumers correlate this normalized row back to the
 *       raw line item, and pairs with {@code tenantId} for the L2 dedup gate.</li>
 *   <li>{@code costUsd} is always USD. AWS values are already USD; GCP rows
 *       carry {@code currency_conversion_rate} (multiplied by the gcp-ingestor
 *       and persisted as {@code cost_usd}). The normalizer doesn't fetch FX.</li>
 *   <li>{@code listPriceUsd} is the pre-discount price — for AWS, equal to
 *       cost (no further discount); for GCP, cost minus the
 *       {@code COMMITTED_USAGE_DISCOUNT} portion of credits (credits are negative
 *       so subtracting them adds back the discount).</li>
 *   <li>{@code commitmentId} is null for on-demand usage; set to the
 *       reservation/CUD identifier for committed usage. Day 14 uses this.</li>
 *   <li>{@code rawSource} ("AWS" / "GCP") names which ingestor emitted the
 *       source event; useful for tracing and partial reprocessing.</li>
 * </ul>
 */
public record CostLineItemNormalized(
        UUID eventId,
        String tenantId,
        String vendor,
        String accountId,
        String team,
        String service,
        String resourceId,
        String region,
        String usageStart,
        String usageEnd,
        Double usageAmount,
        Double costUsd,
        Double listPriceUsd,
        String commitmentId,
        String lineItemType,
        String rawSource
) {
}
