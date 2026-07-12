package io.finflow.saga.orchestration;

import io.finflow.saga.command.SagaCommand;
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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * No-Docker counterpart to SagaHappyPathIT. Drives a saga through all six
 * transitions with SUCCESS events at every step, verifying the state machine
 * (Day 16 pure logic + Day 17 wiring + Day 20 vendor routing) delivers the
 * correct end state.
 *
 * <p>This is what you can run TODAY on the MacBook Air while the Docker
 * runtime issue is unresolved. SagaHappyPathIT proves the same property with
 * a real Postgres + Kafka; SagaHappyPathTest proves it with a real
 * SagaTransitionService (unmocked) and a repository-backed SagaInstance
 * whose state we can inspect.
 */
class SagaHappyPathTest {

    private final SagaInstanceRepository repo = mock(SagaInstanceRepository.class);
    private final SagaCommandEmitter emitter = mock(SagaCommandEmitter.class);
    private final SagaMetrics sagaMetrics = mock(SagaMetrics.class);
    private final SagaTransitionService transitionService = new SagaTransitionService();  // real, unmocked
    private final SagaOrchestrationService orchestration =
            new SagaOrchestrationService(repo, transitionService, emitter, sagaMetrics);

    @Test
    void walksAllFiveStepsToCompleted_forAwsVendor() {
        SagaInstance saga = new SagaInstance(UUID.randomUUID(), SagaType.REBALANCE, "happy-1", Vendor.AWS);
        when(repo.findByCorrelationId("happy-1")).thenReturn(Optional.empty());
        when(repo.findById(saga.getId())).thenReturn(Optional.of(saga));
        when(repo.save(any(SagaInstance.class))).thenAnswer(inv -> inv.getArgument(0));

        orchestration.startRebalance("happy-1", Vendor.AWS);

        for (SagaStep step : List.of(
                SagaStep.ACQUIRE_LOCK,
                SagaStep.VERIFY_COMMITMENT,
                SagaStep.RESERVE_TARGET,
                SagaStep.RELEASE_SOURCE,
                SagaStep.UPDATE_LEDGER)) {
            orchestration.handleEvent(new SagaEvent.StepSucceeded(saga.getId(), step));
        }
        // Day 16's transition: LEDGER_UPDATED needs one more event to move to COMPLETED.
        orchestration.handleEvent(new SagaEvent.StepSucceeded(saga.getId(), SagaStep.UPDATE_LEDGER));

        assertThat(saga.getCurrentState()).isEqualTo(SagaState.COMPLETED);
        assertThat(saga.getCompletedSteps()).containsExactly(
                SagaStep.ACQUIRE_LOCK,
                SagaStep.VERIFY_COMMITMENT,
                SagaStep.RESERVE_TARGET,
                SagaStep.RELEASE_SOURCE,
                SagaStep.UPDATE_LEDGER);
        // 5 Do commands emitted through the walk + the initial one on start.
        verify(emitter, times(5)).emitAll(any(SagaInstance.class), any());
    }
}
