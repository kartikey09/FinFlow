package io.finflow.adapter.aws.consumer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

/**
 * The wire shape saga-orchestrator emits on {@code saga.commands.aws}.
 *
 * <p>This is the adapter's OWN mirror of the contract — INTENTIONALLY not a
 * shared type. Coupling across service boundaries via a shared DTO would be
 * exactly wrong; the JSON contract is the interface, and each service models
 * it independently. If saga-orchestrator adds a field, we ignore it (via
 * {@code @JsonIgnoreProperties(ignoreUnknown = true)}) until we care.
 *
 * <p>{@code direction} decides forward-vs-undo dispatch — both call the same
 * Chaos API endpoint (the chaos-api is stateless) but the outcome is what
 * matters to the orchestrator.
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
