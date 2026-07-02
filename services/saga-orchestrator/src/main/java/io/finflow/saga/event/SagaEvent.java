package io.finflow.saga.event;

import io.finflow.saga.model.SagaStep;

import java.util.UUID;

/**
 * Everything the orchestrator can react to. Two families:
 *
 * <ul>
 *   <li>{@link StepSucceeded} — an adapter worker reports the step it was
 *       asked to do finished cleanly. Names the step so the switch statement
 *       can route without pattern matching.</li>
 *   <li>{@link StepFailed} — the adapter worker reports the step blew up
 *       (Chaos API 503, business precondition failed, whatever). Carries the
 *       failed step and a reason string for logs.</li>
 * </ul>
 *
 * <p>Sealed so {@code SagaTransitionService} can exhaustively pattern-match
 * without a default branch — the compiler catches any new event kind that
 * forgets to update the transition table.
 *
 * <p>All events carry the {@code sagaId} because they arrive on a shared topic;
 * the orchestrator uses it to load the right {@link io.finflow.saga.model.SagaInstance}.
 */
public sealed interface SagaEvent {

    UUID sagaId();

    /** A named step finished successfully. */
    record StepSucceeded(UUID sagaId, SagaStep step) implements SagaEvent {}

    /** A named step failed; {@code reason} is human-readable, for logs. */
    record StepFailed(UUID sagaId, SagaStep step, String reason) implements SagaEvent {}
}
