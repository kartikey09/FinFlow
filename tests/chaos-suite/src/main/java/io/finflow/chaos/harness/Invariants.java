package io.finflow.chaos.harness;

import io.finflow.chaos.harness.SystemSnapshot.CommandCounts;
import io.finflow.chaos.harness.SystemSnapshot.SagaRecord;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The assertion logic, as a PURE FUNCTION of (before, after, expectedSagas).
 *
 * <p>No JDBC, no HTTP, no Docker, no clock. That is the point: this class is the
 * part of the chaos suite that can be unit-tested exhaustively in milliseconds
 * (see InvariantsTest), including all the failure modes that are hard to actually
 * PRODUCE on demand — a double-executed command, a lost ledger write, a saga
 * wedged in COMPENSATING. The live scenarios then just feed it real snapshots.
 *
 * <p>Separating "how do I observe the system" from "what must be true of the
 * system" is what makes the invariants testable at all. Bake the SQL into the
 * assertions and you can only test them by breaking production.
 */
public final class Invariants {

    /** SagaStep has 5 forward steps: ACQUIRE_LOCK, VERIFY_COMMITMENT, RESERVE_TARGET, RELEASE_SOURCE, UPDATE_LEDGER. */
    public static final int FORWARD_STEPS = 5;

    /** Debezium's confirmed_flush_lsn never sits at exactly 0 (heartbeats keep the WAL moving). */
    public static final long SLOT_LAG_TOLERANCE_BYTES = 64 * 1024;   // 64 KiB

    private Invariants() {}

    public static List<InvariantViolation> checkAll(SystemSnapshot before,
                                                    SystemSnapshot after,
                                                    Set<UUID> expectedSagas) {
        List<InvariantViolation> v = new ArrayList<>();
        v.addAll(everySagaReachedAcceptableTerminal(after, expectedSagas));
        v.addAll(noCommandExecutedTwice(after, expectedSagas));
        v.addAll(ledgerIsConsistent(before, after));
        v.addAll(pipelineDrained(after));
        return v;
    }

    // ------------------------------------------------------------------------
    // INV-1 — every saga terminates, and terminates ACCEPTABLY.
    // ------------------------------------------------------------------------
    // "Stuck" is the failure this whole suite exists to catch: a saga sitting in
    // LOCKED or COMPENSATING forever is money in limbo and a human's problem.
    //
    // COMPENSATION_FAILED is TERMINAL but NOT acceptable, and it gets its own
    // violation. It is a real state in the enum, it can genuinely happen (the
    // undo call kept 503-ing past the retry budget), and quietly folding it into
    // "well, it terminated" would hide the single most alarming outcome the
    // system can produce. If this fires, that is a true finding, not a flaky test.
    public static List<InvariantViolation> everySagaReachedAcceptableTerminal(SystemSnapshot after, Set<UUID> expected) {
        List<InvariantViolation> v = new ArrayList<>();
        for (UUID id : expected) {
            SagaRecord s = after.sagas().get(id);
            if (s == null) {
                v.add(new InvariantViolation("SAGA_MISSING",
                        "saga " + id + " was started but has no row in saga.saga_instances"));
                continue;
            }
            if (!s.isTerminal()) {
                v.add(new InvariantViolation("SAGA_STUCK",
                        "saga " + id + " (" + s.correlationId() + ") is STUCK in " + s.currentState()
                                + " after completing " + s.completedSteps()));
            } else if ("COMPENSATION_FAILED".equals(s.currentState())) {
                v.add(new InvariantViolation("COMPENSATION_FAILED",
                        "saga " + id + " (" + s.correlationId() + ") could not undo its own work — "
                                + "terminal, but needs a human. Completed steps: " + s.completedSteps()));
            }
        }
        return v;
    }

    // ------------------------------------------------------------------------
    // INV-2 — no command executed twice. THIS is "no money double-counted".
    // ------------------------------------------------------------------------
    // Each row in {aws,gcp}_adapter.processed_command is one real side effect at
    // the vendor (a lock, a reserve, a release...). idempotency_key is its PRIMARY
    // KEY, so Postgres physically cannot store the same command twice.
    //
    // So what is there to test? THE KEY DERIVATION. Under 50% connection resets,
    // Kafka WILL redeliver commands. If the idempotency key were built from
    // anything volatile — a timestamp, a retry counter, a fresh UUID — a redelivered
    // command would mint a BRAND NEW key, sail past the primary key, and move the
    // money a second time. The PK would still be satisfied. The books would not.
    //
    // Counting rows per saga is what catches that:
    //   COMPLETED   -> exactly FORWARD_STEPS DO rows, 0 UNDO
    //   COMPENSATED -> N DO rows and exactly N UNDO rows (Day 20's pairing)
    //   doCount > FORWARD_STEPS  -> A COMMAND RAN TWICE. Money moved twice.
    public static List<InvariantViolation> noCommandExecutedTwice(SystemSnapshot after, Set<UUID> expected) {
        List<InvariantViolation> v = new ArrayList<>();
        for (UUID id : expected) {
            SagaRecord s = after.sagas().get(id);
            if (s == null) continue;
            CommandCounts c = after.commands().getOrDefault(id, CommandCounts.NONE);

            if (c.doCount() > FORWARD_STEPS) {
                v.add(new InvariantViolation("DOUBLE_EXECUTION",
                        "saga " + id + " executed " + c.doCount() + " DO commands but the saga only has "
                                + FORWARD_STEPS + " steps — a redelivered command was executed twice. "
                                + "MONEY MOVED TWICE."));
            }

            switch (s.currentState()) {
                case "COMPLETED" -> {
                    if (c.doSuccessCount() != FORWARD_STEPS) {
                        v.add(new InvariantViolation("INCOMPLETE_WORK",
                                "saga " + id + " is COMPLETED but only " + c.doSuccessCount() + "/" + FORWARD_STEPS
                                        + " DO commands succeeded"));
                    }
                    if (c.undoCount() != 0) {
                        v.add(new InvariantViolation("SPURIOUS_COMPENSATION",
                                "saga " + id + " is COMPLETED but ran " + c.undoCount() + " UNDO command(s)"));
                    }
                }
                case "COMPENSATED" -> {
                    // Pair UNDO against SUCCESSFUL DO only. The step that failed and
                    // triggered compensation has a processed_command row too
                    // (outcome='FAILURE') but moved no money, so there is nothing to
                    // undo for it. Comparing against total doCount would flag every
                    // compensated saga in the run as broken.
                    if (c.undoCount() != c.doSuccessCount()) {
                        v.add(new InvariantViolation("UNPAIRED_COMPENSATION",
                                "saga " + id + " is COMPENSATED with " + c.doSuccessCount()
                                        + " successful DO but " + c.undoCount() + " UNDO — every step that "
                                        + "actually moved money must be undone exactly once "
                                        + "(Day 20 CompensationPairing)"));
                    }
                }
                default -> { /* stuck / compensation-failed already reported by INV-1 */ }
            }
        }
        return v;
    }

    // ------------------------------------------------------------------------
    // INV-3 — the ledger is exact.
    // ------------------------------------------------------------------------
    // Two checks, and they catch opposite bugs.
    //
    // (a) rows == distinct events. Guaranteed today by UNIQUE(tenant_id, event_id).
    //     Kept as an explicit REGRESSION GUARD: the day someone "optimises" that
    //     constraint away, this fails loudly instead of silently double-billing.
    //     Honest about what it is — it is not proving Postgres works.
    //
    // (b) THE REAL ONE. Every billing event published must land as exactly one
    //     ledger row:  Δledger == Δbilling_events_published.
    //     Too many  -> dedup failed, cost counted twice.
    //     Too few   -> events LOST. Under packet loss that's the scarier bug, and
    //                  a "no duplicates" check alone would never catch it.
    public static List<InvariantViolation> ledgerIsConsistent(SystemSnapshot before, SystemSnapshot after) {
        List<InvariantViolation> v = new ArrayList<>();

        if (after.ledgerRows() != after.ledgerDistinctEvents()) {
            v.add(new InvariantViolation("LEDGER_DUPLICATE",
                    "cost_ledger has " + after.ledgerRows() + " rows but only "
                            + after.ledgerDistinctEvents() + " distinct (tenant_id, event_id) — "
                            + "the UNIQUE constraint is not holding"));
        }

        long ledgerDelta = after.ledgerRows() - before.ledgerRows();
        long publishedDelta = after.billingEventsPublished() - before.billingEventsPublished();

        if (ledgerDelta > publishedDelta) {
            v.add(new InvariantViolation("LEDGER_DOUBLE_WRITE",
                    "cost_ledger grew by " + ledgerDelta + " but only " + publishedDelta
                            + " billing event(s) were published — cost was counted more than once"));
        } else if (ledgerDelta < publishedDelta) {
            v.add(new InvariantViolation("LEDGER_LOST_WRITE",
                    "cost_ledger grew by " + ledgerDelta + " but " + publishedDelta
                            + " billing event(s) were published — " + (publishedDelta - ledgerDelta)
                            + " event(s) never reached the ledger"));
        }
        return v;
    }

    // ------------------------------------------------------------------------
    // INV-4 — the pipeline drained.
    // ------------------------------------------------------------------------
    // The build plan says: "outbox pending count reaches 0 within 60s".
    // In THIS architecture that is not just wrong, it is impossible.
    //
    //   * Debezium NEVER deletes an outbox row. It tails the WAL read-only.
    //   * OutboxRetentionJob deletes them — on cron "0 0 3 * * *", with a 7-DAY
    //     window. So a row written during this test is still there next week.
    //
    // COUNT(outbox_event) therefore measures the RETENTION SCHEDULE, not whether
    // the pipeline is healthy. It will never be 0 and asserting on it is theatre.
    //
    // "Drained" in a CDC architecture means: every outbox row has been READ FROM
    // THE WAL AND CONFIRMED. That is exactly what the replication slot's
    // confirmed_flush_lsn tracks. Lag -> ~0 means Debezium is caught up. Paired
    // with consumer-group lag -> 0, that is the real end-to-end drain check.
    //
    // (The slot never sits at exactly 0 — heartbeats keep the WAL advancing —
    // hence the tolerance.)
    public static List<InvariantViolation> pipelineDrained(SystemSnapshot after) {
        List<InvariantViolation> v = new ArrayList<>();

        after.replicationSlotLagBytes().forEach((slot, lag) -> {
            if (lag > SLOT_LAG_TOLERANCE_BYTES) {
                v.add(new InvariantViolation("OUTBOX_NOT_DRAINED",
                        "replication slot '" + slot + "' is " + lag + " bytes behind (tolerance "
                                + SLOT_LAG_TOLERANCE_BYTES + ") — Debezium has NOT published every outbox row"));
            }
        });

        after.consumerGroupLag().forEach((group, lag) -> {
            if (lag > 0) {
                v.add(new InvariantViolation("CONSUMER_NOT_DRAINED",
                        "consumer group '" + group + "' still has " + lag + " un-consumed record(s)"));
            }
        });
        return v;
    }
}
