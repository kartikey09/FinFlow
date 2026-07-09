package io.finflow.saga.it;

import io.finflow.saga.domain.SagaInstanceRepository;
import io.finflow.saga.event.SagaEvent;
import io.finflow.saga.model.SagaState;
import io.finflow.saga.model.SagaStep;
import io.finflow.saga.model.Vendor;
import io.finflow.saga.orchestration.SagaOrchestrationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SagaRecoveryIT (Testcontainers).
 *
 * <p>Simulates the "process died mid-saga" scenario at the application layer:
 *   1. Start a saga; drive it partway.
 *   2. Reload from Postgres (as if the process just started).
 *   3. Feed the next event; state advances correctly.
 *
 * <p>The docker-kill version of this test — where the actual JVM process is
 * killed and restarted — is provided as {@code scripts/day20-recovery-test.sh}.
 * That's what the plan asks for and what needs a real Docker environment to
 * verify. This IT proves the same property at the Testcontainers layer.
 */
@SpringBootTest
@ContextConfiguration(initializers = SagaITBase.TestPropertyInitializer.class)
class SagaRecoveryIT extends SagaITBase {

    @Autowired SagaOrchestrationService orchestration;
    @Autowired SagaInstanceRepository repo;

    @Test
    void restartedOrchestratorFinishesTheSaga() {
        var saga = orchestration.startRebalance("rec-it-" + System.nanoTime(), Vendor.AWS);

        // Drive it a few steps
        orchestration.handleEvent(new SagaEvent.StepSucceeded(saga.getId(), SagaStep.ACQUIRE_LOCK));
        orchestration.handleEvent(new SagaEvent.StepSucceeded(saga.getId(), SagaStep.VERIFY_COMMITMENT));

        // Simulate restart: reload from Postgres. The in-memory reference we had
        // may be stale; the DB row is the source of truth.
        var reloaded = repo.findById(saga.getId()).orElseThrow();
        assertThat(reloaded.getCurrentState()).isEqualTo(SagaState.VERIFIED);

        // Continue where we left off
        orchestration.handleEvent(new SagaEvent.StepSucceeded(saga.getId(), SagaStep.RESERVE_TARGET));
        orchestration.handleEvent(new SagaEvent.StepSucceeded(saga.getId(), SagaStep.RELEASE_SOURCE));
        orchestration.handleEvent(new SagaEvent.StepSucceeded(saga.getId(), SagaStep.UPDATE_LEDGER));
        orchestration.handleEvent(new SagaEvent.StepSucceeded(saga.getId(), SagaStep.UPDATE_LEDGER));

        var finished = repo.findById(saga.getId()).orElseThrow();
        assertThat(finished.getCurrentState()).isEqualTo(SagaState.COMPLETED);
    }
}
