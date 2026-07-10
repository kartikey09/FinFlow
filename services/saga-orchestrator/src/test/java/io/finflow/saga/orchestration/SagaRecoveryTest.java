package io.finflow.saga.orchestration;

import io.finflow.saga.domain.SagaInstanceRepository;
import io.finflow.saga.event.SagaEvent;
import io.finflow.saga.metrics.SagaMetrics;
import io.finflow.saga.model.SagaInstance;
import io.finflow.saga.model.SagaState;
import io.finflow.saga.model.SagaStep;
import io.finflow.saga.model.SagaType;
import io.finflow.saga.model.Vendor;
import io.finflow.saga.transition.SagaTransitionService;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * No-Docker counterpart to SagaRecoveryIT.
 *
 * <p>Recovery in this architecture is passive: everything the orchestrator
 * needs to resume lives on the SagaInstance row (current_state +
 * completed_steps). This test proves that a "restarted" orchestration service
 * (represented by a fresh instance) picks up exactly where it left off given
 * the SagaInstance state and a redelivered Kafka event.
 *
 * <p>The plan's docker-kill test (bash script — see scripts/day20-recovery-test.sh)
 * proves the same property against a real Postgres + Kafka. This test proves
 * it at the code level.
 */
class SagaRecoveryTest {

    private final SagaInstanceRepository repo = mock(SagaInstanceRepository.class);
    private final SagaCommandEmitter emitter = mock(SagaCommandEmitter.class);
    private final SagaMetrics sagaMetrics = mock(SagaMetrics.class);

    @Test
    void restartedOrchestratorResumesFromLastCommittedState() {
        // Simulate DB state after a crash: saga was at TARGET_RESERVED, three steps completed.
        SagaInstance saga = new SagaInstance(UUID.randomUUID(), SagaType.REBALANCE, "rec-1", Vendor.AWS);
        saga.applyTransition(SagaState.LOCKED, SagaStep.ACQUIRE_LOCK);
        saga.applyTransition(SagaState.VERIFIED, SagaStep.VERIFY_COMMITMENT);
        saga.applyTransition(SagaState.TARGET_RESERVED, SagaStep.RESERVE_TARGET);

        // Fresh instance of the orchestration service — the "restarted" process.
        SagaOrchestrationService restarted = new SagaOrchestrationService(repo, new SagaTransitionService(), emitter, sagaMetrics);

        when(repo.findById(saga.getId())).thenReturn(Optional.of(saga));
        when(repo.save(any(SagaInstance.class))).thenAnswer(inv -> inv.getArgument(0));

        // Kafka redelivers the event that was pending when we crashed: step 4 succeeded.
        restarted.handleEvent(new SagaEvent.StepSucceeded(saga.getId(), SagaStep.RELEASE_SOURCE));

        assertThat(saga.getCurrentState()).isEqualTo(SagaState.SOURCE_RELEASED);
        assertThat(saga.getCompletedSteps()).containsExactly(
                SagaStep.ACQUIRE_LOCK,
                SagaStep.VERIFY_COMMITMENT,
                SagaStep.RESERVE_TARGET,
                SagaStep.RELEASE_SOURCE);

        // The next event drives it to LEDGER_UPDATED
        restarted.handleEvent(new SagaEvent.StepSucceeded(saga.getId(), SagaStep.UPDATE_LEDGER));
        // Then one more transitions to COMPLETED
        restarted.handleEvent(new SagaEvent.StepSucceeded(saga.getId(), SagaStep.UPDATE_LEDGER));
        assertThat(saga.getCurrentState()).isEqualTo(SagaState.COMPLETED);
    }
}
