package io.finflow.adapter.aws.emit;

import java.util.UUID;

/**
 * The wire shape the adapter publishes to {@code saga.events}. Matches the
 * shape saga-orchestrator's {@code SagaEventPayload} parses (independently —
 * neither side depends on the other's class).
 *
 * <p>Contract: {@code success = true} maps to {@code StepSucceeded} on the
 * orchestrator side; {@code false} to {@code StepFailed} with the reason.
 */
public record SagaEventPayload(
        UUID sagaId,
        String step,
        boolean success,
        String reason
) {}
