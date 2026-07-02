package io.finflow.saga.model;

/**
 * The named forward steps of a rebalance saga.
 *
 * <p>Persisted as strings inside the {@code completed_steps} JSONB array. When
 * compensation kicks in, the orchestrator walks this list in REVERSE and emits
 * the paired undo command for each one — you only undo what you actually did,
 * which is exactly what the completed-steps stack encodes.
 *
 * <p>Kept in a separate enum from {@link SagaState} on purpose: a state
 * describes where the saga IS; a step describes what it DID. Conflating them
 * leads to a state machine where every state has to carry a stack — this way
 * the responsibilities stay clean and the switch statement stays short.
 */
public enum SagaStep {
    ACQUIRE_LOCK,
    VERIFY_COMMITMENT,
    RESERVE_TARGET,
    RELEASE_SOURCE,
    UPDATE_LEDGER
}
