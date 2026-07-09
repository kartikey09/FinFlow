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
import io.finflow.saga.transition.TransitionResult;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.OptimisticLockingFailureException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies the Day-17 transactional bridge without booting Spring or touching
 * Kafka/DB. The properties that matter:
 *
 *   1. startRebalance is IDEMPOTENT on correlationId (returns the existing saga,
 *      no second save, no second emit).
 *   2. handleEvent applies the pure evaluate result to the row AND emits any
 *      commands — proved by capturing both the saga save and the emitAll call.
 *   3. A no-op transition (e.g. a redelivery) doesn't touch the DB or the outbox.
 *   4. An OptimisticLockingFailureException from save is RETHROWN so Kafka
 *      redelivers — this is the "recovery story is free" claim in action.
 */
class SagaOrchestrationServiceTest {

    private final SagaInstanceRepository sagaRepository = mock(SagaInstanceRepository.class);
    private final SagaTransitionService transitionService = mock(SagaTransitionService.class);
    private final SagaCommandEmitter commandEmitter = mock(SagaCommandEmitter.class);

    private final SagaOrchestrationService orchestration =
            new SagaOrchestrationService(sagaRepository, transitionService, commandEmitter);

    @Test
    void startRebalance_firstTime_createsSagaAndEmitsInitialCommand() {
        String correlation = "corr-100";
        when(sagaRepository.findByCorrelationId(correlation)).thenReturn(Optional.empty());
        when(sagaRepository.save(any(SagaInstance.class))).thenAnswer(inv -> inv.getArgument(0));

        SagaInstance saga = orchestration.startRebalance(correlation, Vendor.AWS);

        ArgumentCaptor<List<SagaCommand>> cmdCaptor = commandsCaptor();
        verify(commandEmitter).emitAll(any(SagaInstance.class), cmdCaptor.capture());
        assertThat(cmdCaptor.getValue())
                .containsExactly(new SagaCommand.Do(saga.getId(), SagaStep.ACQUIRE_LOCK));
        assertThat(saga.getCorrelationId()).isEqualTo(correlation);
        assertThat(saga.getCurrentState()).isEqualTo(SagaState.STARTED);
    }

    @Test
    void startRebalance_idempotent_returnsExistingSagaWithoutEmitting() {
        SagaInstance existing = new SagaInstance(UUID.randomUUID(), SagaType.REBALANCE, "corr-200", Vendor.AWS);
        when(sagaRepository.findByCorrelationId("corr-200")).thenReturn(Optional.of(existing));

        SagaInstance result = orchestration.startRebalance("corr-200", Vendor.AWS);

        assertThat(result).isSameAs(existing);
        verify(sagaRepository, never()).save(any());
        verify(commandEmitter, never()).emitAll(any(), any());
    }

    @Test
    void handleEvent_appliesTransitionAndEmitsCommands() {
        SagaInstance saga = new SagaInstance(UUID.randomUUID(), SagaType.REBALANCE, "corr-300", Vendor.AWS);
        SagaEvent event = new SagaEvent.StepSucceeded(saga.getId(), SagaStep.ACQUIRE_LOCK);
        SagaCommand nextCmd = new SagaCommand.Do(saga.getId(), SagaStep.VERIFY_COMMITMENT);

        when(sagaRepository.findById(saga.getId())).thenReturn(Optional.of(saga));
        when(transitionService.evaluate(SagaState.STARTED, saga.getCompletedSteps(), event))
                .thenReturn(new TransitionResult(SagaState.LOCKED, SagaStep.ACQUIRE_LOCK, List.of(nextCmd)));

        orchestration.handleEvent(event);

        assertThat(saga.getCurrentState()).isEqualTo(SagaState.LOCKED);
        assertThat(saga.getCompletedSteps()).containsExactly(SagaStep.ACQUIRE_LOCK);
        verify(sagaRepository).save(saga);
        verify(commandEmitter).emitAll(any(SagaInstance.class), eq(List.of(nextCmd)));
    }

    @Test
    void handleEvent_noOpTransition_savesNothing_emitsNothing() {
        // e.g. a Kafka redelivery of an event we already advanced past.
        SagaInstance saga = new SagaInstance(UUID.randomUUID(), SagaType.REBALANCE, "corr-400", Vendor.AWS);
        saga.applyTransition(SagaState.LOCKED, SagaStep.ACQUIRE_LOCK);
        SagaEvent event = new SagaEvent.StepSucceeded(saga.getId(), SagaStep.ACQUIRE_LOCK);

        when(sagaRepository.findById(saga.getId())).thenReturn(Optional.of(saga));
        // A no-op TransitionResult: same state, no step recorded, no commands.
        when(transitionService.evaluate(eq(SagaState.LOCKED), any(), eq(event)))
                .thenReturn(TransitionResult.of(SagaState.LOCKED));

        orchestration.handleEvent(event);

        verify(sagaRepository, never()).save(any());
        verify(commandEmitter, never()).emitAll(any(), any());
    }

    @Test
    void handleEvent_unknownSaga_isDroppedNotThrown() {
        UUID unknownId = UUID.randomUUID();
        SagaEvent event = new SagaEvent.StepSucceeded(unknownId, SagaStep.ACQUIRE_LOCK);
        when(sagaRepository.findById(unknownId)).thenReturn(Optional.empty());

        orchestration.handleEvent(event);   // does not throw

        verify(transitionService, never()).evaluate(any(), any(), any());
        verify(commandEmitter, never()).emitAll(any(), any());
    }

    @Test
    void handleEvent_optimisticLockException_isRethrownSoKafkaCanRedeliver() {
        SagaInstance saga = new SagaInstance(UUID.randomUUID(), SagaType.REBALANCE, "corr-500", Vendor.AWS);
        SagaEvent event = new SagaEvent.StepSucceeded(saga.getId(), SagaStep.ACQUIRE_LOCK);
        SagaCommand nextCmd = new SagaCommand.Do(saga.getId(), SagaStep.VERIFY_COMMITMENT);

        when(sagaRepository.findById(saga.getId())).thenReturn(Optional.of(saga));
        when(transitionService.evaluate(any(), any(), eq(event)))
                .thenReturn(new TransitionResult(SagaState.LOCKED, SagaStep.ACQUIRE_LOCK, List.of(nextCmd)));
        when(sagaRepository.save(any())).thenThrow(
                new OptimisticLockingFailureException("row updated by another instance"));

        assertThatThrownBy(() -> orchestration.handleEvent(event))
                .isInstanceOf(OptimisticLockingFailureException.class);
    }

    // Silence unchecked-cast warnings on the ArgumentCaptor<List<...>> pattern.
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ArgumentCaptor<List<SagaCommand>> commandsCaptor() {
        return (ArgumentCaptor<List<SagaCommand>>) (ArgumentCaptor) ArgumentCaptor.forClass(List.class);
    }
}
