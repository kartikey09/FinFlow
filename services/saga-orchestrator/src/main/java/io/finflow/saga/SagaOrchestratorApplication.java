package io.finflow.saga;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The saga-orchestrator — the heart of Week 4.
 *
 * <p>Day 16's scope is deliberately tight: the core state machine and its unit
 * tests, no Kafka, no outbox, no adapter workers. That wiring is Day 17.
 *
 * <p>The philosophy of the whole week, from the build plan: "No framework, no
 * throwaway module — you go straight at the real thing because the real thing
 * is tractable." No Spring Statemachine dependency; the transition table IS a
 * switch statement, ~150 lines of Java a tired engineer can debug.
 *
 * <p>What the orchestrator will end up doing (by Day 20):
 * <ol>
 *   <li>Receive {@code POST /sagas/rebalance} from the dashboard.</li>
 *   <li>Persist a {@code SagaInstance} and emit the first command via the
 *       outbox — one transaction.</li>
 *   <li>React to command-result events on {@code saga.events}: load the saga,
 *       evaluate the transition, persist the new state, emit the next command
 *       — one transaction per hop.</li>
 *   <li>On failure, walk the {@code completedSteps} stack in reverse and emit
 *       compensation commands.</li>
 *   <li>Recover from a crash by reading whichever state the row was in when
 *       the last transaction committed.</li>
 * </ol>
 */
@SpringBootApplication
public class SagaOrchestratorApplication {
    public static void main(String[] args) {
        SpringApplication.run(SagaOrchestratorApplication.class, args);
    }
}
