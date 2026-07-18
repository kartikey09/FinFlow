package io.finflow.chaos;

import io.finflow.chaos.harness.InvariantViolation;
import io.finflow.chaos.harness.Invariants;
import io.finflow.chaos.harness.SystemSnapshot;
import io.finflow.chaos.harness.SystemSnapshot.CommandCounts;
import io.finflow.chaos.harness.SystemSnapshot.SagaRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TIER 1 — NO DOCKER, NO KAFKA, NO POSTGRES, NO NETWORK. Runs in milliseconds as part
 * of `./gradlew test` and stays green forever.
 *
 * <p>This is the half of the chaos suite that can actually be tested. The live scenarios
 * (ChaosSuiteIT) can only assert that nothing went wrong on a particular run — they
 * cannot prove the assertions themselves are RIGHT. If the invariant logic silently
 * accepted a double-executed command, a green chaos run would prove nothing at all.
 *
 * <p>So the invariants are a pure function, and here we hand-build the exact failures
 * that are hard or impossible to produce on demand — a saga wedged in COMPENSATING, a
 * command that ran twice, a ledger write that vanished — and assert the checker catches
 * each one. THE TESTS TEST THE TEST.
 *
 * <p>Bonus: this also runs in an environment with no Docker at all, which is the
 * situation the rest of this repo's integration tests have been stuck in since Day 20.
 */
class InvariantsTest {

    private static final UUID S1 = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID S2 = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private static final List<String> ALL_STEPS = List.of(
            "ACQUIRE_LOCK", "VERIFY_COMMITMENT", "RESERVE_TARGET", "RELEASE_SOURCE", "UPDATE_LEDGER");

    // ---------- fixture builders ----------

    private static SystemSnapshot snapshot(Map<UUID, SagaRecord> sagas,
                                           Map<UUID, CommandCounts> commands,
                                           long ledgerRows, long ledgerDistinct, long published,
                                           Map<String, Long> slotLag, Map<String, Long> consumerLag) {
        return new SystemSnapshot(sagas, commands, ledgerRows, ledgerDistinct, published, slotLag, consumerLag);
    }

    /** A clean, drained, nothing-wrong "after" snapshot. */
    private static SystemSnapshot healthy(Map<UUID, SagaRecord> sagas, Map<UUID, CommandCounts> commands) {
        return snapshot(sagas, commands, 100, 100, 100,
                Map.of("finflow_outbox_slot", 0L, "finflow_saga_outbox_slot", 0L),
                Map.of("saga-orchestrator", 0L, "aws-adapter", 0L));
    }

    private static SystemSnapshot before() {
        return snapshot(Map.of(), Map.of(), 100, 100, 100,
                Map.of("finflow_outbox_slot", 0L), Map.of("saga-orchestrator", 0L));
    }

    private static SagaRecord saga(UUID id, String state, List<String> steps) {
        return new SagaRecord(id, "chaos-run-x", state, steps);
    }

    private static List<String> codes(List<InvariantViolation> vs) {
        return vs.stream().map(InvariantViolation::invariant).toList();
    }

    // ------------------------------------------------------------------
    @Nested
    @DisplayName("INV-1: every saga must terminate, and terminate acceptably")
    class Termination {

        @Test
        @DisplayName("20 sagas all COMPLETED -> clean")
        void twentyCompleted() {
            Map<UUID, SagaRecord> sagas = new LinkedHashMap<>();
            Map<UUID, CommandCounts> cmds = new LinkedHashMap<>();
            for (int i = 0; i < 20; i++) {
                UUID id = UUID.randomUUID();
                sagas.put(id, saga(id, "COMPLETED", ALL_STEPS));
                cmds.put(id, new CommandCounts(5, 5, 0));
            }
            var v = Invariants.checkAll(before(), healthy(sagas, cmds), sagas.keySet());
            assertThat(v).isEmpty();
        }

        @Test
        @DisplayName("a saga wedged in COMPENSATING is STUCK — the failure this suite exists for")
        void stuckSaga() {
            var sagas = Map.of(S1, saga(S1, "COMPENSATING", List.of("ACQUIRE_LOCK", "VERIFY_COMMITMENT")));
            var cmds = Map.of(S1, new CommandCounts(3, 2, 1));
            var v = Invariants.checkAll(before(), healthy(sagas, cmds), Set.of(S1));
            assertThat(codes(v)).contains("SAGA_STUCK");
            assertThat(v.toString()).contains("COMPENSATING");
        }

        @Test
        @DisplayName("a saga wedged in LOCKED is STUCK")
        void stuckInLocked() {
            var sagas = Map.of(S1, saga(S1, "LOCKED", List.of("ACQUIRE_LOCK")));
            var v = Invariants.checkAll(before(),
                    healthy(sagas, Map.of(S1, new CommandCounts(1, 1, 0))), Set.of(S1));
            assertThat(codes(v)).contains("SAGA_STUCK");
        }

        @Test
        @DisplayName("COMPENSATION_FAILED is terminal but NOT acceptable — reported separately, never swallowed")
        void compensationFailed() {
            var sagas = Map.of(S1, saga(S1, "COMPENSATION_FAILED", List.of("ACQUIRE_LOCK", "VERIFY_COMMITMENT")));
            var cmds = Map.of(S1, new CommandCounts(3, 2, 1));
            var v = Invariants.checkAll(before(), healthy(sagas, cmds), Set.of(S1));
            assertThat(codes(v)).contains("COMPENSATION_FAILED");
            assertThat(codes(v)).doesNotContain("SAGA_STUCK");   // it IS terminal
        }

        @Test
        @DisplayName("a saga we started that has no row at all")
        void sagaMissing() {
            var v = Invariants.checkAll(before(), healthy(Map.of(), Map.of()), Set.of(S1));
            assertThat(codes(v)).contains("SAGA_MISSING");
        }
    }

    // ------------------------------------------------------------------
    @Nested
    @DisplayName("INV-2: no command executed twice — 'no money double-counted'")
    class DoubleExecution {

        @Test
        @DisplayName("6 DO commands for a 5-step saga -> DOUBLE_EXECUTION. Money moved twice.")
        void redeliveredCommandRanTwice() {
            var sagas = Map.of(S1, saga(S1, "COMPLETED", ALL_STEPS));
            var cmds = Map.of(S1, new CommandCounts(6, 6, 0));   // idempotency key derivation broke
            var v = Invariants.checkAll(before(), healthy(sagas, cmds), Set.of(S1));
            assertThat(codes(v)).contains("DOUBLE_EXECUTION");
            assertThat(v.toString()).contains("MONEY MOVED TWICE");
        }

        @Test
        @DisplayName("COMPLETED but only 3 steps actually executed -> INCOMPLETE_WORK")
        void completedWithMissingWork() {
            var sagas = Map.of(S1, saga(S1, "COMPLETED", ALL_STEPS));
            var cmds = Map.of(S1, new CommandCounts(3, 3, 0));
            var v = Invariants.checkAll(before(), healthy(sagas, cmds), Set.of(S1));
            assertThat(codes(v)).contains("INCOMPLETE_WORK");
        }

        @Test
        @DisplayName("COMPLETED but compensation ran anyway -> SPURIOUS_COMPENSATION")
        void completedButCompensated() {
            var sagas = Map.of(S1, saga(S1, "COMPLETED", ALL_STEPS));
            var cmds = Map.of(S1, new CommandCounts(5, 5, 2));
            var v = Invariants.checkAll(before(), healthy(sagas, cmds), Set.of(S1));
            assertThat(codes(v)).contains("SPURIOUS_COMPENSATION");
        }

        @Test
        @DisplayName("REGRESSION GUARD: a compensated saga whose failed step wrote a FAILURE row must NOT be flagged")
        void compensatedWithAFailedStepIsFine() {
            // 3 DO rows: 2 SUCCESS + 1 FAILURE (the step that blew up and triggered the undo).
            // Only the 2 successful steps moved money, so only 2 UNDOs are correct.
            // Pairing UNDO against TOTAL doCount (3) would flag this — and would flag
            // EVERY compensated saga in the run. That bug is exactly why doSuccessCount exists.
            var sagas = Map.of(S1, saga(S1, "COMPENSATED", List.of("ACQUIRE_LOCK", "VERIFY_COMMITMENT")));
            var cmds = Map.of(S1, new CommandCounts(3, 2, 2));
            var v = Invariants.checkAll(before(), healthy(sagas, cmds), Set.of(S1));
            assertThat(v).isEmpty();
        }

        @Test
        @DisplayName("COMPENSATED with an un-undone step -> UNPAIRED_COMPENSATION")
        void compensatedButNotFullyUndone() {
            var sagas = Map.of(S1, saga(S1, "COMPENSATED", List.of("ACQUIRE_LOCK", "VERIFY_COMMITMENT")));
            var cmds = Map.of(S1, new CommandCounts(3, 2, 1));   // 2 succeeded, only 1 undone
            var v = Invariants.checkAll(before(), healthy(sagas, cmds), Set.of(S1));
            assertThat(codes(v)).contains("UNPAIRED_COMPENSATION");
        }
    }

    // ------------------------------------------------------------------
    @Nested
    @DisplayName("INV-3: the ledger is exact")
    class Ledger {

        @Test
        @DisplayName("ledger grew by exactly the number of events published -> clean")
        void exact() {
            var after = snapshot(Map.of(), Map.of(), 150, 150, 150,
                    Map.of("finflow_outbox_slot", 0L), Map.of("g", 0L));
            var v = Invariants.checkAll(before(), after, Set.of());
            assertThat(v).isEmpty();
        }

        @Test
        @DisplayName("ledger grew MORE than events published -> LEDGER_DOUBLE_WRITE (cost counted twice)")
        void doubleWrite() {
            var after = snapshot(Map.of(), Map.of(), 160, 160, 150,   // +60 rows, +50 events
                    Map.of("finflow_outbox_slot", 0L), Map.of("g", 0L));
            var v = Invariants.checkAll(before(), after, Set.of());
            assertThat(codes(v)).contains("LEDGER_DOUBLE_WRITE");
        }

        @Test
        @DisplayName("ledger grew LESS than events published -> LEDGER_LOST_WRITE (a 'no duplicates' check would MISS this)")
        void lostWrite() {
            var after = snapshot(Map.of(), Map.of(), 140, 140, 150,   // +40 rows, +50 events
                    Map.of("finflow_outbox_slot", 0L), Map.of("g", 0L));
            var v = Invariants.checkAll(before(), after, Set.of());
            assertThat(codes(v)).contains("LEDGER_LOST_WRITE");
            assertThat(v.toString()).contains("never reached the ledger");
        }

        @Test
        @DisplayName("REGRESSION GUARD: rows != distinct events means UNIQUE(tenant_id, event_id) was dropped")
        void uniqueConstraintGone() {
            var after = snapshot(Map.of(), Map.of(), 150, 145, 150,
                    Map.of("finflow_outbox_slot", 0L), Map.of("g", 0L));
            var v = Invariants.checkAll(before(), after, Set.of());
            assertThat(codes(v)).contains("LEDGER_DUPLICATE");
        }
    }

    // ------------------------------------------------------------------
    @Nested
    @DisplayName("INV-4: the pipeline drained (slot lag, NOT outbox row count)")
    class Drain {

        @Test
        @DisplayName("Debezium still behind -> OUTBOX_NOT_DRAINED")
        void debeziumBehind() {
            var after = snapshot(Map.of(), Map.of(), 100, 100, 100,
                    Map.of("finflow_saga_outbox_slot", 5L * 1024 * 1024),   // 5 MiB of unread WAL
                    Map.of("g", 0L));
            var v = Invariants.checkAll(before(), after, Set.of());
            assertThat(codes(v)).contains("OUTBOX_NOT_DRAINED");
        }

        @Test
        @DisplayName("small slot lag is tolerated — heartbeats keep the WAL moving, it is never exactly 0")
        void smallLagTolerated() {
            var after = snapshot(Map.of(), Map.of(), 100, 100, 100,
                    Map.of("finflow_saga_outbox_slot", 1024L),
                    Map.of("g", 0L));
            var v = Invariants.checkAll(before(), after, Set.of());
            assertThat(v).isEmpty();
        }

        @Test
        @DisplayName("a consumer still behind -> CONSUMER_NOT_DRAINED")
        void consumerBehind() {
            var after = snapshot(Map.of(), Map.of(), 100, 100, 100,
                    Map.of("finflow_outbox_slot", 0L),
                    Map.of("aws-adapter", 12L));
            var v = Invariants.checkAll(before(), after, Set.of());
            assertThat(codes(v)).contains("CONSUMER_NOT_DRAINED");
        }
    }

    // ------------------------------------------------------------------
    @Test
    @DisplayName("a run can violate several invariants at once — ALL of them are reported, not just the first")
    void reportsEveryViolation() {
        var sagas = new LinkedHashMap<UUID, SagaRecord>();
        sagas.put(S1, saga(S1, "COMPENSATING", List.of("ACQUIRE_LOCK")));        // stuck
        sagas.put(S2, saga(S2, "COMPLETED", ALL_STEPS));                          // double-executed
        var cmds = Map.of(
                S1, new CommandCounts(1, 1, 0),
                S2, new CommandCounts(7, 7, 0));
        var after = snapshot(sagas, cmds, 160, 160, 150,                          // ledger double-write
                Map.of("finflow_saga_outbox_slot", 9_000_000L),                   // debezium behind
                Map.of("aws-adapter", 3L));                                       // consumer behind

        var v = Invariants.checkAll(before(), after, Set.of(S1, S2));

        assertThat(codes(v)).contains(
                "SAGA_STUCK", "DOUBLE_EXECUTION", "LEDGER_DOUBLE_WRITE",
                "OUTBOX_NOT_DRAINED", "CONSUMER_NOT_DRAINED");
        assertThat(v).hasSizeGreaterThanOrEqualTo(5);
    }
}
