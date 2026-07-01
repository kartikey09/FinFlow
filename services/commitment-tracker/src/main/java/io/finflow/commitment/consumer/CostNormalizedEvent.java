package io.finflow.commitment.consumer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The commitment-tracker's view of the canonical cost event emitted by
 * cost-normalizer (Day 13).
 *
 * <p>DELIBERATELY NOT SHARED — this is THIS service's mirror of the contract.
 * Sharing a DTO across service boundaries (via shared/common) would couple them;
 * a cross-service contract should live in JSON, not in classpath. We only
 * declare the fields THIS service reads — cost, the commitment id (the gate),
 * and the usage start (so we can derive the period day). Everything else is
 * ignored via {@code @JsonIgnoreProperties(ignoreUnknown = true)} so additive
 * schema changes from the normalizer never break this consumer.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CostNormalizedEvent(
        String commitmentId,   // the gate: null = on-demand event, skip
        Double costUsd,
        String usageStart,     // ISO-8601; the source of period_day
        String tenantId,
        String accountId
) {}
