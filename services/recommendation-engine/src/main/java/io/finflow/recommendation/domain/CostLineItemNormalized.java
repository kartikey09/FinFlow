package io.finflow.recommendation.domain;

import java.util.UUID;

/**
 * Inbound event from cost-normalizer (finflow.events.cost-normalized). The engine
 * only cares about a few fields — accountId, service, region, costUsd, and crucially
 * commitmentId (null == on-demand spend a commitment could have covered) — but the
 * record mirrors the full published shape so deserialization is exact.
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
) {}
