package io.finflow.recommendation.domain;

import java.util.UUID;

/**
 * The event this service publishes to finflow.events.recommendation (via the outbox).
 *
 * <p>NOTE ON THE TOPIC NAME: the build plan calls the output topic
 * "recommendations.created". FinFlow's Debezium EventRouter routes every outbox row
 * by aggregate_type into "finflow.events.${aggregate_type}", so with aggregate_type
 * "recommendation" this lands on finflow.events.recommendation. Special-casing the
 * connector to emit a differently-named topic for this one producer would break the
 * uniform outbox->CDC pattern that every other service relies on, for no benefit.
 * The event TYPE is "RecommendationCreated"; the topic follows the house convention.
 * query-api subscribes to finflow.events.recommendation accordingly.
 */
public record RecommendationCreated(
        UUID id,
        String tenantId,
        String type,
        String commitmentId,
        String accountId,
        String vendor,
        String service,
        String region,
        double estimatedSavingsUsd,
        String evidenceJson
) {}
