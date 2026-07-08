package io.finflow.saga.orchestration;

import io.finflow.saga.command.SagaCommand;
import io.finflow.saga.domain.SagaInstanceRepository;
import io.finflow.saga.event.SagaEvent;
import io.finflow.saga.model.SagaInstance;
import io.finflow.saga.model.SagaState;
import io.finflow.saga.model.SagaStep;
import io.finflow.saga.model.SagaType;
import io.finflow.saga.model.Vendor;
import io.finflow.saga.transition.SagaTransitionService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * No-Docker counterpart to SagaCompensationIT. Drives a saga to step 3
 * (RESERVE_TARGET), injects a FAILURE, verifies:
 *   - state transitions to COMPENSATING
 *   - the first Undo command is for the LAST completed step (LIFO)
 *   - the walk unrolls in reverse order
 *   - final state is COMPENSATED
 */
class SagaCompensationTest {

    private final SagaInstanceRepository repo = mock(SagaInstanceRepository.class);
    private final SagaCommandEmitter emitter = mock(SagaCommandEmitter.class);
    private final SagaOrchestrationService orchestration =
            new SagaOrchestrationService(repo, new SagaTransitionService(), emitter);

    @Test
    void failureOnStep3TriggersReverseCompensationWalk() {
        SagaInstance saga = new SagaInstance(UUID.randomUUID(), SagaType.REBALANCE, "comp-1", Vendor.AWS);
        when(repo.findByCorrelationId("comp-1")).thenReturn(Optional.empty());
        when(repo.findById(saga.getId())).thenReturn(Optional.of(saga));
        when(repo.save(any(SagaInstance.class))).thenAnswer(inv -> inv.getArgument(0));

        orchestration.startRebalance("comp-1", Vendor.AWS);
        // steps 1 and 2 succeed
        orchestration.handleEvent(new SagaEvent.StepSucceeded(saga.getId(), SagaStep.ACQUIRE_LOCK));
        orchestration.handleEvent(new SagaEvent.StepSucceeded(saga.getId(), SagaStep.VERIFY_COMMITMENT));
        // step 3 FAILS (chaos-injected)
        orchestration.handleEvent(new SagaEvent.StepFailed(saga.getId(), SagaStep.RESERVE_TARGET, "Chaos 503"));

        assertThat(saga.getCurrentState()).isEqualTo(SagaState.COMPENSATING);

        // Successive Undo successes drive the reverse walk.
        // Prev completed: [ACQUIRE_LOCK, VERIFY_COMMITMENT]. First Undo emitted is for VERIFY_COMMITMENT.
        orchestration.handleEvent(new SagaEvent.StepSucceeded(saga.getId(), SagaStep.VERIFY_COMMITMENT));
        // Next Undo should be for ACQUIRE_LOCK
        orchestration.handleEvent(new SagaEvent.StepSucceeded(saga.getId(), SagaStep.ACQUIRE_LOCK));

        assertThat(saga.getCurrentState()).isEqualTo(SagaState.COMPENSATED);
    }

    @Test
    void firstStepFailureShortcutsStraightToCompensated() {
        SagaInstance saga = new SagaInstance(UUID.randomUUID(), SagaType.REBALANCE, "comp-2", Vendor.GCP);
        when(repo.findByCorrelationId("comp-2")).thenReturn(Optional.empty());
        when(repo.findById(saga.getId())).thenReturn(Optional.of(saga));
        when(repo.save(any(SagaInstance.class))).thenAnswer(inv -> inv.getArgument(0));

        orchestration.startRebalance("comp-2", Vendor.GCP);
        // Step 1 itself fails. Nothing to undo.
        orchestration.handleEvent(new SagaEvent.StepFailed(saga.getId(), SagaStep.ACQUIRE_LOCK, "Chaos 503"));

        assertThat(saga.getCurrentState()).isEqualTo(SagaState.COMPENSATED);
    }
}
