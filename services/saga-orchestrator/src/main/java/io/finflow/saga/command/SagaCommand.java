package io.finflow.saga.command;

import io.finflow.saga.model.SagaStep;

import java.util.UUID;

/**
 * A command the orchestrator asks an adapter worker to execute.
 *
 * <p>Two families, symmetric to the two event families:
 * <ul>
 *   <li>{@link Do} — do this step forward.</li>
 *   <li>{@link Undo} — undo this step (compensation).</li>
 * </ul>
 *
 * <p>Both carry the {@link SagaStep} they refer to, which lets ONE adapter
 * worker per step handle both the forward and the reverse operation — a nice
 * property when Day 18 arrives. And it lets the same {@code Do(RESERVE_TARGET)}
 * type describe a forward reservation and {@code Undo(RESERVE_TARGET)} the
 * matching unreserve.
 *
 * <p>Sealed for the same exhaustiveness benefits {@code SagaEvent} enjoys.
 */
public sealed interface SagaCommand {

    UUID sagaId();
    SagaStep step();

    /** Execute the step forward. */
    record Do(UUID sagaId, SagaStep step) implements SagaCommand {}

    /** Execute the step in reverse (compensation). */
    record Undo(UUID sagaId, SagaStep step) implements SagaCommand {}
}
