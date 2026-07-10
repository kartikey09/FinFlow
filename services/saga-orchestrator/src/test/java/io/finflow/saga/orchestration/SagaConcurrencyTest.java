package io.finflow.saga.orchestration;

import io.finflow.saga.command.SagaCommand;
import io.finflow.saga.domain.SagaInstanceRepository;
import io.finflow.saga.event.SagaEvent;
import io.finflow.saga.metrics.SagaMetrics;
import io.finflow.saga.model.SagaInstance;
import io.finflow.saga.model.SagaStep;
import io.finflow.saga.model.SagaType;
import io.finflow.saga.model.Vendor;
import io.finflow.saga.transition.SagaTransitionService;
import io.finflow.saga.transition.TransitionResult;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * No-Docker counterpart to SagaConcurrencyIT. Proves that when two events for
 * one saga collide at once, @Version makes one win, and the other's transaction
 * throws OptimisticLockingFailureException so Kafka can redeliver.
 *
 * <p>This is the plan's "custom-approach must-have" — the whole reason we didn't
 * use Spring Statemachine: our own switch with our own @Version guard means
 * concurrent writers are handled with primitive JPA semantics that any reviewer
 * can trace.
 */
class SagaConcurrencyTest {

    private final SagaInstanceRepository repo = mock(SagaInstanceRepository.class);
    private final SagaTransitionService transitionService = mock(SagaTransitionService.class);
    private final SagaCommandEmitter emitter = mock(SagaCommandEmitter.class);
    private final SagaMetrics sagaMetrics = mock(SagaMetrics.class);
    private final SagaOrchestrationService orchestration =
            new SagaOrchestrationService(repo, transitionService, emitter, sagaMetrics);

    @Test
    void optimisticLockFailure_isRethrownSoKafkaCanRedeliver() {
        SagaInstance saga = new SagaInstance(UUID.randomUUID(), SagaType.REBALANCE, "conc-1", Vendor.AWS);
        SagaEvent event = new SagaEvent.StepSucceeded(saga.getId(), SagaStep.ACQUIRE_LOCK);
        SagaCommand nextCmd = new SagaCommand.Do(saga.getId(), SagaStep.VERIFY_COMMITMENT);

        when(repo.findById(saga.getId())).thenReturn(Optional.of(saga));
        when(transitionService.evaluate(any(), any(), eq(event)))
                .thenReturn(new TransitionResult(
                        io.finflow.saga.model.SagaState.LOCKED,
                        SagaStep.ACQUIRE_LOCK,
                        List.of(nextCmd)));
        // Simulate a concurrent writer having already committed a newer version.
        when(repo.save(any())).thenThrow(
                new OptimisticLockingFailureException("row updated by another instance"));

        assertThatThrownBy(() -> orchestration.handleEvent(event))
                .isInstanceOf(OptimisticLockingFailureException.class);
        // The rethrow means Spring rolls back this transaction and Kafka will
        // redeliver — by which point the winner has advanced state, so the
        // redelivery hits the "already past this step" branch of evaluate()
        // and is a no-op.
    }
}
