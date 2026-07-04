package io.finflow.adapter.gcp.consumer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

/**
 * The wire shape saga-orchestrator emits on {@code saga.commands.gcp}.
 * Independent from aws-adapter-worker's mirror on purpose — sharing a DTO
 * across service boundaries is exactly the coupling we want to avoid; the
 * JSON contract is the interface, each service models it.
 *
 * <p>Additive schema changes upstream never break us because
 * {@code @JsonIgnoreProperties(ignoreUnknown = true)} tolerates them.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SagaCommand(
        UUID sagaId,
        String step,
        String direction,          // "DO" | "UNDO"
        String idempotencyKey
) {
    public boolean isUndo() {
        return "UNDO".equals(direction);
    }
}
