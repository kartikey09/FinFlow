package io.finflow.chaos;

import io.finflow.chaos.harness.ChaosStack;
import io.finflow.chaos.harness.InvariantViolation;
import io.finflow.chaos.harness.Invariants;
import io.finflow.chaos.harness.ServiceProcess;
import io.finflow.chaos.harness.SystemSnapshot;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * TIER 2 — the live chaos scenarios. Requires a RUNNING stack.
 *
 *   docker compose -f docker-compose.yml -f docker-compose.chaos.yml up -d
 *   ./gradlew bootJar
 *   ./gradlew :tests:chaos-suite:chaosTest
 *
 * <p>Deliberately NOT part of `./gradlew build` — it takes minutes, it kills processes,
 * and it needs infrastructure. `test` runs InvariantsTest only.
 *
 * <p>Every scenario has the same skeleton, because the invariants are the same no matter
 * how you break the system:
 *
 *   snapshot BEFORE -> inject fault -> drive 20 sagas -> HEAL -> wait for drain
 *   -> snapshot AFTER -> Invariants.checkAll(...) -> assert zero violations
 *
 * <p>The HEAL step matters. If you leave the fault on while waiting for the system to
 * settle, you are not testing recovery — you are testing whether it can converge while
 * still being attacked, which it cannot and should not. Real resilience is: survive the
 * fault, then converge once it lifts.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ChaosSuiteIT {

    private static final Logger log = LoggerFactory.getLogger(ChaosSuiteIT.class);

    private static final int SAGAS = 20;                             // the plan's N
    private static final int CHAOS_FAULT_RATE = 30;                  // the plan's 30%
    private static final double KAFKA_TOXICITY = 0.5;                // the plan's "50% packet loss"
    private static final Duration DRAIN_TIMEOUT = Duration.ofMinutes(4);
    private static final Duration BOOT = Duration.ofSeconds(90);

    private static ChaosStack stack;

    @BeforeAll
    static void bootTheWorld() throws Exception {
        stack = new ChaosStack();
        stack.preflight();
        stack.startSagaPath();
    }

    @AfterEach
    void heal() throws Exception {
        // Never let one scenario's fault leak into the next.
        stack.chaos.reset();
        stack.toxiproxy.healAll();
        for (String name : List.of("saga-orchestrator", "aws-adapter-worker", "chaos-api")) {
            ServiceProcess p = stack.service(name);
            if (!p.isAlive()) p.restart(BOOT);
        }
    }

    @AfterAll
    static void teardown() {
        if (stack != null) stack.close();
    }

    // =======================================================================
    @Test
    @Order(1)
    @DisplayName("S1 — 20 concurrent sagas at 30% Chaos API fault rate")
    void chaosAt30Percent() throws Exception {
        String run = runId("s1-chaos30");
        SystemSnapshot before = stack.snapshots.read(run);

        // APPLICATION-layer chaos: the fake vendor 503s and hangs.
        // This is what Resilience4j (Retry/CB/TimeLimiter/Bulkhead) is for.
        stack.chaos.enabled(true);
        stack.chaos.faultRate(CHAOS_FAULT_RATE);

        List<UUID> ids = stack.sagas.fireConcurrently(run, SAGAS, "AWS");
        assertThat(ids).as("all %s sagas must at least be ACCEPTED", SAGAS).hasSize(SAGAS);

        stack.chaos.reset();                 // heal, THEN let it converge
        awaitTerminalAndDrained(run, ids);

        assertNoViolations(before, run, ids);
    }

    // =======================================================================
    @Test
    @Order(2)
    @DisplayName("S2 — 20 sagas while Toxiproxy resets 50% of Kafka connections")
    void kafkaConnectionResets() throws Exception {
        String run = runId("s2-toxi50");
        SystemSnapshot before = stack.snapshots.read(run);

        // TRANSPORT-layer chaos: half of all Kafka TCP connections die mid-stream.
        // Nothing Resilience4j can do about this — it forces consumer-group rebalances
        // and Kafka REDELIVERY, which is the only way to actually test idempotency.
        // (Toxiproxy has no packet-loss toxic; reset_peer @ toxicity 0.5 is the faithful
        //  and harsher translation. See ToxiproxyControl.)
        stack.toxiproxy.resetPeer(KAFKA_TOXICITY);

        List<UUID> ids = stack.sagas.fireConcurrently(run, SAGAS, "AWS");

        stack.toxiproxy.healAll();
        awaitTerminalAndDrained(run, ids);

        assertNoViolations(before, run, ids);
    }

    // =======================================================================
    @Test
    @Order(3)
    @DisplayName("S3 — SIGKILL saga-orchestrator mid-flight; it must resume its own in-flight sagas on restart")
    void killSagaOrchestratorMidFlight() throws Exception {
        String run = runId("s3-kill-saga");
        SystemSnapshot before = stack.snapshots.read(run);

        stack.chaos.enabled(true);
        stack.chaos.faultRate(10);           // a little chaos, so some sagas are compensating when we kill

        List<UUID> ids = stack.sagas.fireConcurrently(run, SAGAS, "AWS");

        // Kill at a RANDOM point so we don't accidentally always hit the same step boundary.
        // The sagas are mid-flight: some LOCKED, some VERIFIED, some COMPENSATING.
        long delay = ThreadLocalRandom.current().nextLong(800, 4_000);
        Thread.sleep(delay);
        log.info("--- SIGKILL saga-orchestrator {}ms in, with {} sagas in flight ---", delay, ids.size());
        stack.service("saga-orchestrator").killHard();

        Thread.sleep(2_000);                 // let it be dead for a moment
        stack.service("saga-orchestrator").restart(BOOT);

        // THE POINT OF THIS SCENARIO: nobody re-drives these sagas. If they finish, it is
        // because Day 17's findAllInFlight() resume logic picked them up on boot. This is
        // the first time that code path has ever actually been exercised.
        stack.chaos.reset();
        awaitTerminalAndDrained(run, ids);

        assertNoViolations(before, run, ids);
    }

    // =======================================================================
    @Test
    @Order(4)
    @DisplayName("S4 — SIGKILL aws-adapter-worker mid-command; the redelivered command must NOT execute twice")
    void killAwsAdapterMidCommand() throws Exception {
        String run = runId("s4-kill-adapter");
        SystemSnapshot before = stack.snapshots.read(run);

        List<UUID> ids = stack.sagas.fireConcurrently(run, SAGAS, "AWS");

        // Kill the adapter while it is executing commands. Its Kafka offsets are NOT
        // committed (ack-mode: manual, and SIGKILL means no shutdown hook), so on restart
        // Kafka REDELIVERS every command it was working on.
        //
        // Worst case: it had already called the Chaos API — really moved the money — and
        // died before acking. On redelivery it MUST recognise the idempotency key in
        // processed_command and skip. If it doesn't, the money moves twice.
        //
        // That is precisely what Invariants.noCommandExecutedTwice measures.
        Thread.sleep(ThreadLocalRandom.current().nextLong(600, 2_500));
        log.info("--- SIGKILL aws-adapter-worker mid-command ---");
        stack.service("aws-adapter-worker").killHard();

        Thread.sleep(1_500);
        stack.service("aws-adapter-worker").restart(BOOT);

        awaitTerminalAndDrained(run, ids);

        assertNoViolations(before, run, ids);
    }

    // =======================================================================
    // helpers
    // =======================================================================

    private static String runId(String label) {
        return label + "-" + Long.toHexString(System.currentTimeMillis());
    }

    /**
     * Wait until every saga in the run is terminal AND the CDC pipeline has caught up.
     *
     * <p>Note what is NOT here: a check that COUNT(outbox_event) == 0. That never happens.
     * Debezium doesn't delete outbox rows and OutboxRetentionJob only sweeps at 3am with a
     * 7-day window, so the row count measures the retention schedule, not pipeline health.
     * The real question is whether Debezium has READ and CONFIRMED everything — which is
     * the replication slot's confirmed_flush_lsn. See Invariants.pipelineDrained.
     */
    private void awaitTerminalAndDrained(String run, List<UUID> ids) {
        await("all sagas terminal")
                .atMost(DRAIN_TIMEOUT)
                .pollInterval(Duration.ofSeconds(2))
                .until(() -> {
                    SystemSnapshot s = stack.snapshots.read(run);
                    return ids.stream().allMatch(id -> {
                        var rec = s.sagas().get(id);
                        return rec != null && rec.isTerminal();
                    });
                });

        await("CDC + consumers drained")
                .atMost(Duration.ofMinutes(2))
                .pollInterval(Duration.ofSeconds(2))
                .until(() -> {
                    SystemSnapshot s = stack.snapshots.read(run);
                    return Invariants.pipelineDrained(s).isEmpty();
                });
    }

    private void assertNoViolations(SystemSnapshot before, String run, List<UUID> ids) throws Exception {
        SystemSnapshot after = stack.snapshots.read(run);
        List<InvariantViolation> violations = Invariants.checkAll(before, after, Set.copyOf(ids));

        if (!violations.isEmpty()) {
            log.error("=== {} INVARIANT VIOLATION(S) ===", violations.size());
            violations.forEach(v -> log.error("  {}", v));
        }
        assertThat(violations)
                .as("chaos run %s: every saga must end COMPLETED or COMPENSATED, no command may run "
                        + "twice, the ledger must be exact, and the pipeline must drain", run)
                .isEmpty();
    }
}
