package io.finflow.saga.transition;

import io.finflow.saga.command.SagaCommand;
import io.finflow.saga.event.SagaEvent;
import io.finflow.saga.model.SagaState;
import io.finflow.saga.model.SagaStep;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The exhaustive proof that the state machine is correct — pure JUnit, no
 * Spring context, no database, no Kafka. This is what Day 16 is FOR: to be
 * able to change one line of the switch and see the whole rebalance flow
 * either stay valid or fail loudly.
 *
 * <p>Organized as nested classes matching the two chains — forward and
 * compensation — plus a small "degenerate cases" section for redeliveries and
 * terminal-state ignores.
 */
class SagaTransitionServiceTest {

    private final SagaTransitionService service = new SagaTransitionService();
    private final UUID sagaId = UUID.randomUUID();

    // =========================================================================
    //  FORWARD CHAIN
    // =========================================================================

    @Nested
    class ForwardChain {

        @Test
        void startedPlusAcquireLockSucceeded_thenVerifyCommand() {
            TransitionResult r = service.evaluate(
                    SagaState.STARTED,
                    List.of(),
                    new SagaEvent.StepSucceeded(sagaId, SagaStep.ACQUIRE_LOCK));

            assertThat(r.nextState()).isEqualTo(SagaState.LOCKED);
            assertThat(r.justCompleted()).isEqualTo(SagaStep.ACQUIRE_LOCK);
            assertThat(r.commandsToEmit())
                    .containsExactly(new SagaCommand.Do(sagaId, SagaStep.VERIFY_COMMITMENT));
        }

        @Test
        void lockedPlusVerifyCommitmentSucceeded_thenReserveTargetCommand() {
            TransitionResult r = service.evaluate(
                    SagaState.LOCKED,
                    List.of(SagaStep.ACQUIRE_LOCK),
                    new SagaEvent.StepSucceeded(sagaId, SagaStep.VERIFY_COMMITMENT));

            assertThat(r.nextState()).isEqualTo(SagaState.VERIFIED);
            assertThat(r.justCompleted()).isEqualTo(SagaStep.VERIFY_COMMITMENT);
            assertThat(r.commandsToEmit())
                    .containsExactly(new SagaCommand.Do(sagaId, SagaStep.RESERVE_TARGET));
        }

        @Test
        void verifiedPlusReserveTargetSucceeded_thenReleaseSourceCommand() {
            TransitionResult r = service.evaluate(
                    SagaState.VERIFIED,
                    List.of(SagaStep.ACQUIRE_LOCK, SagaStep.VERIFY_COMMITMENT),
                    new SagaEvent.StepSucceeded(sagaId, SagaStep.RESERVE_TARGET));

            assertThat(r.nextState()).isEqualTo(SagaState.TARGET_RESERVED);
            assertThat(r.commandsToEmit())
                    .containsExactly(new SagaCommand.Do(sagaId, SagaStep.RELEASE_SOURCE));
        }

        @Test
        void targetReservedPlusReleaseSourceSucceeded_thenUpdateLedgerCommand() {
            TransitionResult r = service.evaluate(
                    SagaState.TARGET_RESERVED,
                    List.of(SagaStep.ACQUIRE_LOCK, SagaStep.VERIFY_COMMITMENT, SagaStep.RESERVE_TARGET),
                    new SagaEvent.StepSucceeded(sagaId, SagaStep.RELEASE_SOURCE));

            assertThat(r.nextState()).isEqualTo(SagaState.SOURCE_RELEASED);
            assertThat(r.commandsToEmit())
                    .containsExactly(new SagaCommand.Do(sagaId, SagaStep.UPDATE_LEDGER));
        }

        @Test
        void sourceReleasedPlusUpdateLedgerSucceeded_thenLedgerUpdatedNoMoreCommands() {
            TransitionResult r = service.evaluate(
                    SagaState.SOURCE_RELEASED,
                    List.of(SagaStep.ACQUIRE_LOCK, SagaStep.VERIFY_COMMITMENT,
                            SagaStep.RESERVE_TARGET, SagaStep.RELEASE_SOURCE),
                    new SagaEvent.StepSucceeded(sagaId, SagaStep.UPDATE_LEDGER));

            assertThat(r.nextState()).isEqualTo(SagaState.LEDGER_UPDATED);
            assertThat(r.justCompleted()).isEqualTo(SagaStep.UPDATE_LEDGER);
            assertThat(r.commandsToEmit()).isEmpty();
        }

        @Test
        void endToEndRebalance_walksAllSixStatesToCompleted() {
            SagaState state = SagaState.STARTED;
            List<SagaStep> completed = new ArrayList<>();
            List<SagaStep> orderedSteps = List.of(
                    SagaStep.ACQUIRE_LOCK, SagaStep.VERIFY_COMMITMENT,
                    SagaStep.RESERVE_TARGET, SagaStep.RELEASE_SOURCE,
                    SagaStep.UPDATE_LEDGER);

            for (SagaStep step : orderedSteps) {
                TransitionResult r = service.evaluate(
                        state, completed, new SagaEvent.StepSucceeded(sagaId, step));
                state = r.nextState();
                if (r.justCompleted() != null) {
                    completed.add(r.justCompleted());
                }
            }
            // One last transition: LEDGER_UPDATED -> COMPLETED (driven by any event, per the design).
            TransitionResult finalR = service.evaluate(
                    state, completed, new SagaEvent.StepSucceeded(sagaId, SagaStep.UPDATE_LEDGER));

            assertThat(finalR.nextState()).isEqualTo(SagaState.COMPLETED);
            assertThat(completed).containsExactly(
                    SagaStep.ACQUIRE_LOCK, SagaStep.VERIFY_COMMITMENT,
                    SagaStep.RESERVE_TARGET, SagaStep.RELEASE_SOURCE,
                    SagaStep.UPDATE_LEDGER);
        }
    }

    // =========================================================================
    //  COMPENSATION CHAIN
    // =========================================================================

    @Nested
    class CompensationChain {

        @Test
        void midSagaFailure_startsCompensationWithFirstUndo() {
            // Two forward steps done, third (RESERVE_TARGET) fails.
            List<SagaStep> completed = List.of(SagaStep.ACQUIRE_LOCK, SagaStep.VERIFY_COMMITMENT);

            TransitionResult r = service.evaluate(
                    SagaState.VERIFIED,
                    completed,
                    new SagaEvent.StepFailed(sagaId, SagaStep.RESERVE_TARGET, "Chaos API 503"));

            assertThat(r.nextState()).isEqualTo(SagaState.COMPENSATING);
            assertThat(r.justCompleted()).isNull();       // we're undoing, not doing
            assertThat(r.commandsToEmit())
                    .containsExactly(new SagaCommand.Undo(sagaId, SagaStep.VERIFY_COMMITMENT));
        }

        @Test
        void firstStepFailure_shortcutsStraightToCompensated() {
            // Nothing to undo — the very first step fails.
            TransitionResult r = service.evaluate(
                    SagaState.STARTED,
                    List.of(),
                    new SagaEvent.StepFailed(sagaId, SagaStep.ACQUIRE_LOCK, "Chaos API 503"));

            assertThat(r.nextState()).isEqualTo(SagaState.COMPENSATED);
            assertThat(r.commandsToEmit()).isEmpty();
        }

        @Test
        void compensationWalksTheStackInReverse() {
            List<SagaStep> completed = List.of(
                    SagaStep.ACQUIRE_LOCK, SagaStep.VERIFY_COMMITMENT, SagaStep.RESERVE_TARGET);

            // First undo (of RESERVE_TARGET) succeeded — next command should undo VERIFY_COMMITMENT.
            TransitionResult r1 = service.evaluate(
                    SagaState.COMPENSATING,
                    completed,
                    new SagaEvent.StepSucceeded(sagaId, SagaStep.RESERVE_TARGET));
            assertThat(r1.nextState()).isEqualTo(SagaState.COMPENSATING);
            assertThat(r1.commandsToEmit())
                    .containsExactly(new SagaCommand.Undo(sagaId, SagaStep.VERIFY_COMMITMENT));

            // Undo of VERIFY_COMMITMENT succeeded — next command should undo ACQUIRE_LOCK.
            List<SagaStep> afterFirstUndo = List.of(SagaStep.ACQUIRE_LOCK, SagaStep.VERIFY_COMMITMENT);
            TransitionResult r2 = service.evaluate(
                    SagaState.COMPENSATING,
                    afterFirstUndo,
                    new SagaEvent.StepSucceeded(sagaId, SagaStep.VERIFY_COMMITMENT));
            assertThat(r2.commandsToEmit())
                    .containsExactly(new SagaCommand.Undo(sagaId, SagaStep.ACQUIRE_LOCK));

            // Undo of ACQUIRE_LOCK succeeded — everything is undone.
            List<SagaStep> afterSecondUndo = List.of(SagaStep.ACQUIRE_LOCK);
            TransitionResult r3 = service.evaluate(
                    SagaState.COMPENSATING,
                    afterSecondUndo,
                    new SagaEvent.StepSucceeded(sagaId, SagaStep.ACQUIRE_LOCK));
            assertThat(r3.nextState()).isEqualTo(SagaState.COMPENSATED);
            assertThat(r3.commandsToEmit()).isEmpty();
        }

        @Test
        void anUndoFailure_isTheWorstFailure_lockedForHumanAttention() {
            TransitionResult r = service.evaluate(
                    SagaState.COMPENSATING,
                    List.of(SagaStep.ACQUIRE_LOCK, SagaStep.VERIFY_COMMITMENT),
                    new SagaEvent.StepFailed(sagaId, SagaStep.VERIFY_COMMITMENT, "adapter crashed"));

            assertThat(r.nextState()).isEqualTo(SagaState.COMPENSATION_FAILED);
            assertThat(r.commandsToEmit()).isEmpty();
        }
    }

    // =========================================================================
    //  DEGENERATE CASES — the ones that keep production quiet
    // =========================================================================

    @Nested
    class DegenerateCases {

        @Test
        void duplicateSuccessForAnAlreadyCompletedStep_staysPut_noCommand() {
            // We're already at LOCKED; a redelivery of AcquireLock arrives.
            TransitionResult r = service.evaluate(
                    SagaState.LOCKED,
                    List.of(SagaStep.ACQUIRE_LOCK),
                    new SagaEvent.StepSucceeded(sagaId, SagaStep.ACQUIRE_LOCK));

            assertThat(r.nextState()).isEqualTo(SagaState.LOCKED);
            assertThat(r.justCompleted()).isNull();
            assertThat(r.commandsToEmit()).isEmpty();
        }

        @Test
        void unexpectedSuccessForAWrongStep_staysPut_noCommand() {
            // Adapter reports UPDATE_LEDGER succeeded but we're only at LOCKED.
            TransitionResult r = service.evaluate(
                    SagaState.LOCKED,
                    List.of(SagaStep.ACQUIRE_LOCK),
                    new SagaEvent.StepSucceeded(sagaId, SagaStep.UPDATE_LEDGER));

            assertThat(r.nextState()).isEqualTo(SagaState.LOCKED);
            assertThat(r.commandsToEmit()).isEmpty();
        }

        @Test
        void eventsToATerminalSagaAreIgnored() {
            for (SagaState terminal : List.of(
                    SagaState.COMPLETED, SagaState.COMPENSATED, SagaState.COMPENSATION_FAILED)) {
                TransitionResult r = service.evaluate(
                        terminal,
                        List.of(SagaStep.ACQUIRE_LOCK),
                        new SagaEvent.StepFailed(sagaId, SagaStep.ACQUIRE_LOCK, "late arrival"));

                assertThat(r.nextState())
                        .as("terminal state %s should stay put", terminal)
                        .isEqualTo(terminal);
                assertThat(r.commandsToEmit()).isEmpty();
            }
        }
    }
}
