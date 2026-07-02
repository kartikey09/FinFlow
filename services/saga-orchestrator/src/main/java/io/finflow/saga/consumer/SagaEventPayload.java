package io.finflow.saga.consumer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.finflow.saga.model.SagaStep;

import java.util.UUID;

/**
 * The wire shape adapter workers publish to {@code saga.events} — the
 * orchestrator's own view of the response. Each carries:
 *
 * <ul>
 *   <li>{@code sagaId} — routes the event to its saga row.</li>
 *   <li>{@code step} — which command this is a result for.</li>
 *   <li>{@code success} — happy path vs. failure.</li>
 *   <li>{@code reason} — human-readable, populated on failure.</li>
 * </ul>
 *
 * <p>Deliberately independent from the adapter's outbound DTO — the orchestrator
 * models the contract, not the adapter. Additive schema changes from adapters
 * never break us because unknown JSON keys are ignored.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SagaEventPayload(
        UUID sagaId,
        SagaStep step,
        boolean success,
        String reason
) {}
