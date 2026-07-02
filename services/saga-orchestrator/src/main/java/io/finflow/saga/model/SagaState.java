package io.finflow.saga.model;

/**
 * The complete set of states a rebalance saga can be in.
 *
 * <p>Two chains:
 * <ul>
 *   <li><b>Forward</b> — the happy path, six steps:
 *       {@link #STARTED} → {@link #LOCKED} → {@link #VERIFIED} →
 *       {@link #TARGET_RESERVED} → {@link #SOURCE_RELEASED} →
 *       {@link #LEDGER_UPDATED} → {@link #COMPLETED}.</li>
 *   <li><b>Compensation</b> — reached from any forward state on a step failure:
 *       {@link #COMPENSATING} → {@link #COMPENSATED} (all undos succeeded) or
 *       {@link #COMPENSATION_FAILED} (an undo itself failed — a human's problem
 *       from that point on).</li>
 * </ul>
 *
 * <p>Persisted as strings in {@code saga_instances.current_state} so a rename
 * here without a migration is a hard database error at boot, not silent
 * corruption. Order intentionally matches the forward flow so
 * {@code state.ordinal()} tells you how far the saga got — useful for logging.
 */
public enum SagaState {

    /** Row created; first command not yet emitted (transient — {@code evaluate} moves off this immediately). */
    STARTED,

    /** {@code AcquireLock} succeeded — the source commitment is locked against concurrent rebalance. */
    LOCKED,

    /** {@code VerifyCommitment} succeeded — pre-conditions for the swap check out. */
    VERIFIED,

    /** {@code ReserveTarget} succeeded — target commitment slot reserved. */
    TARGET_RESERVED,

    /** {@code ReleaseSource} succeeded — source commitment freed. */
    SOURCE_RELEASED,

    /** {@code UpdateLedger} succeeded — the cost_ledger reflects the new attribution. */
    LEDGER_UPDATED,

    /** Terminal happy path — no more commands emitted from here. */
    COMPLETED,

    /** A forward step failed; the orchestrator is walking the completed-steps stack in reverse. */
    COMPENSATING,

    /** Terminal — every completed step has been successfully undone. */
    COMPENSATED,

    /** Terminal — an undo itself failed. Needs manual investigation; the saga will not retry itself. */
    COMPENSATION_FAILED;

    /** Is this a terminal state? (No further events processed.) */
    public boolean isTerminal() {
        return this == COMPLETED || this == COMPENSATED || this == COMPENSATION_FAILED;
    }
}
