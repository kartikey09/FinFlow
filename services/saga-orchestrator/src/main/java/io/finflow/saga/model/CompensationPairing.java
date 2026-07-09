package io.finflow.saga.model;

/**
 * The plan's compensation-pairing table, encoded as a lookup.
 *
 * <pre>
 *   Forward step               Undo semantics
 *   ─────────────────────      ──────────────
 *   ACQUIRE_LOCK               UnlockCommitment       (release the lock)
 *   VERIFY_COMMITMENT          [NO-OP]                (verification is read-only)
 *   RESERVE_TARGET             UnreserveCommitment    (release the target reservation)
 *   RELEASE_SOURCE             ReReserveSource        (undo the source release)
 *   UPDATE_LEDGER              RevertLedger   OR "page human" (honest)
 * </pre>
 *
 * <p><b>The UPDATE_LEDGER caveat is deliberate and per the plan:</b> the ledger
 * update is the last step in the chain, and reverting it cleanly requires
 * either an audit log or a compensating debit. For the demo we treat the
 * undo of UPDATE_LEDGER as a call to the same {@code /update-ledger} endpoint
 * — the chaos-api is stateless so this is well-defined; a real system would
 * either implement RevertLedger correctly or escalate to a human via a
 * COMPENSATION_FAILED transition.
 *
 * <p><b>VERIFY_COMMITMENT has no meaningful undo</b> — it was a read-only
 * pre-check. The transition service still walks past it (an Undo(VERIFY_COMMITMENT)
 * command is emitted for symmetry), but the adapter treats it as a no-op success.
 *
 * <p>Not used at runtime today; kept as documentation-that-compiles and a
 * hook for a future "should this step be compensated?" filter.
 */
public final class CompensationPairing {

    private CompensationPairing() {}

    /** Whether the undo of this step is a real reversal (true) or a documented no-op (false). */
    public static boolean isReversible(SagaStep step) {
        return switch (step) {
            case ACQUIRE_LOCK      -> true;    // release the lock
            case VERIFY_COMMITMENT -> false;   // read-only — nothing to undo
            case RESERVE_TARGET    -> true;    // release the reservation
            case RELEASE_SOURCE    -> true;    // re-reserve
            case UPDATE_LEDGER     -> true;    // debit or "page human" — see class doc
        };
    }

    /**
     * Human-readable label for the undo action. Used in logs so a reviewer can
     * follow the compensation walk without cross-referencing this class.
     */
    public static String undoLabel(SagaStep step) {
        return switch (step) {
            case ACQUIRE_LOCK      -> "UnlockCommitment";
            case VERIFY_COMMITMENT -> "NoOpUndoVerify";
            case RESERVE_TARGET    -> "UnreserveCommitment";
            case RELEASE_SOURCE    -> "ReReserveSource";
            case UPDATE_LEDGER     -> "RevertLedger";
        };
    }
}
