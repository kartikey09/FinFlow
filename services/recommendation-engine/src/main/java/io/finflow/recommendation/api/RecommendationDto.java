package io.finflow.recommendation.api;

import io.finflow.recommendation.store.Recommendation;

/**
 * The API view of a recommendation. Flattens the entity and drops the internal
 * bookkeeping (last_event_id, timestamps) the dashboard doesn't need.
 */
public record RecommendationDto(
        String id,
        String type,
        String commitmentId,
        String accountId,
        String vendor,
        String service,
        String region,
        double estimatedSavingsUsd,
        String evidence
) {
    public static RecommendationDto from(Recommendation r) {
        return new RecommendationDto(
                r.getId().toString(),
                r.getType(),
                r.getCommitmentId(),
                r.getAccountId(),
                r.getVendor(),
                r.getService(),
                r.getRegion(),
                r.getEstimatedSavingsUsd(),
                r.getEvidence());
    }
}
