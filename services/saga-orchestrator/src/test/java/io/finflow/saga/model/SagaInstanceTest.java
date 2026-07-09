package io.finflow.saga.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The entity is thin, but two properties matter enough to pin down:
 *   1. applyTransition atomically records the step and moves state.
 *   2. getCompletedSteps returns a read-only view — callers can't mutate the
 *      internal list from outside (a bug that would silently corrupt state).
 */
class SagaInstanceTest {

    @Test
    void applyTransition_recordsStepAndMovesState() {
        SagaInstance saga = new SagaInstance(UUID.randomUUID(), SagaType.REBALANCE, "corr-1", Vendor.AWS);
        assertThat(saga.getCurrentState()).isEqualTo(SagaState.STARTED);

        saga.applyTransition(SagaState.LOCKED, SagaStep.ACQUIRE_LOCK);

        assertThat(saga.getCurrentState()).isEqualTo(SagaState.LOCKED);
        assertThat(saga.getCompletedSteps()).containsExactly(SagaStep.ACQUIRE_LOCK);
    }

    @Test
    void applyTransition_movesStateWithoutRecordingIfStepIsNull() {
        // Compensation transitions pass a null step because they're unwinding, not doing.
        SagaInstance saga = new SagaInstance(UUID.randomUUID(), SagaType.REBALANCE, "corr-2", Vendor.AWS);
        saga.applyTransition(SagaState.LOCKED, SagaStep.ACQUIRE_LOCK);

        saga.applyTransition(SagaState.COMPENSATING, null);

        assertThat(saga.getCurrentState()).isEqualTo(SagaState.COMPENSATING);
        assertThat(saga.getCompletedSteps()).containsExactly(SagaStep.ACQUIRE_LOCK);  // unchanged
    }

    @Test
    void getCompletedSteps_returnsUnmodifiableView() {
        SagaInstance saga = new SagaInstance(UUID.randomUUID(), SagaType.REBALANCE, "corr-3", Vendor.AWS);
        saga.applyTransition(SagaState.LOCKED, SagaStep.ACQUIRE_LOCK);

        assertThatThrownBy(() -> saga.getCompletedSteps().add(SagaStep.VERIFY_COMMITMENT))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
