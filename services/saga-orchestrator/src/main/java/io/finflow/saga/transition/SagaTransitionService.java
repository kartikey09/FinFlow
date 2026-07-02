package io.finflow.saga.transition;

import io.finflow.saga.command.SagaCommand;
import io.finflow.saga.event.SagaEvent;
import io.finflow.saga.model.SagaState;
import io.finflow.saga.model.SagaStep;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The state machine at the heart of the saga.
 *
 * <p><b>Pure function.</b> {@link #evaluate} takes the current state, the list
 * of steps already completed, and the incoming event, and returns the next
 * state plus the commands to emit. It reads nothing (no repository, no clock),
 * writes nothing (no persistence, no Kafka), throws nothing (an unexpected
 * event returns "stay put, do nothing"). That purity is the WHOLE POINT of
 * Day 16: it can be unit-tested exhaustively with plain JUnit, and Day 17's
 * job is only to wrap it in a transaction.
 *
 * <p><b>Two conceptual chains.</b> Forward is a linear ladder:
 * {@code STARTED → LOCKED → VERIFIED → TARGET_RESERVED → SOURCE_RELEASED →
 * LEDGER_UPDATED → COMPLETED}. Compensation is the reverse walk of
 * {@code completedSteps}, driven by the caller feeding in synthetic events
 * one at a time (Day 17's job). This service just answers: "given I'm HERE
 * and THIS happened, where do I go and what do I ask for next?"
 *
 * <p><b>The switch statement <em>is</em> the transition table.</b> Not a rules
 * engine, not a YAML file, not Spring Statemachine — just Java. This is the
 * plan's exact argument: 150 lines of code you can read in one sitting and
 * step through in a debugger at 2am.
 *
 * <p><b>Idempotency.</b> A duplicate {@code StepSucceeded} for a step that's
 * already been recorded (state has advanced past it) is a NO-OP: return the
 * current state, no commands. Kafka redelivery is thereby harmless without
 * needing a separate deduplication table for events.
 */
@Service
public class SagaTransitionService {

    /**
     * The whole state machine, top to bottom.
     *
     * @param currentState    where the saga is
     * @param completedSteps  what the saga has done (needed for compensation walk)
     * @param event           what just arrived
     * @return the next state, the step (if any) that just completed, and the
     *         commands to emit; NEVER null.
     */
    public TransitionResult evaluate(SagaState currentState,
                                     List<SagaStep> completedSteps,
                                     SagaEvent event) {
        if (currentState.isTerminal()) {
            return TransitionResult.of(currentState);   // late redelivery; do nothing
        }

        return switch (event) {
            case SagaEvent.StepSucceeded ok    -> onSuccess(currentState, completedSteps, ok);
            case SagaEvent.StepFailed    fail  -> onFailure(currentState, completedSteps, fail);
        };
    }

    // ------------------------------------------------------------------------
    //  FORWARD path
    // ------------------------------------------------------------------------

    private TransitionResult onSuccess(SagaState state, List<SagaStep> done,
                                       SagaEvent.StepSucceeded ok) {

        // Compensation success events (from Undo commands): tick another step
        // off the stack. When the stack is empty, we're done compensating.
        if (state == SagaState.COMPENSATING) {
            return advanceCompensation(ok.sagaId(), done);
        }

        // Forward path: each state expects a specific step's success. Anything
        // else is either a redelivery (already past this step) or garbage; in
        // both cases we stay put and emit nothing.
        return switch (state) {
            case STARTED -> ok.step() == SagaStep.ACQUIRE_LOCK
                    ? TransitionResult.of(SagaState.LOCKED, SagaStep.ACQUIRE_LOCK,
                            new SagaCommand.Do(ok.sagaId(), SagaStep.VERIFY_COMMITMENT))
                    : TransitionResult.of(state);

            case LOCKED -> ok.step() == SagaStep.VERIFY_COMMITMENT
                    ? TransitionResult.of(SagaState.VERIFIED, SagaStep.VERIFY_COMMITMENT,
                            new SagaCommand.Do(ok.sagaId(), SagaStep.RESERVE_TARGET))
                    : TransitionResult.of(state);

            case VERIFIED -> ok.step() == SagaStep.RESERVE_TARGET
                    ? TransitionResult.of(SagaState.TARGET_RESERVED, SagaStep.RESERVE_TARGET,
                            new SagaCommand.Do(ok.sagaId(), SagaStep.RELEASE_SOURCE))
                    : TransitionResult.of(state);

            case TARGET_RESERVED -> ok.step() == SagaStep.RELEASE_SOURCE
                    ? TransitionResult.of(SagaState.SOURCE_RELEASED, SagaStep.RELEASE_SOURCE,
                            new SagaCommand.Do(ok.sagaId(), SagaStep.UPDATE_LEDGER))
                    : TransitionResult.of(state);

            case SOURCE_RELEASED -> ok.step() == SagaStep.UPDATE_LEDGER
                    ? TransitionResult.of(SagaState.LEDGER_UPDATED, SagaStep.UPDATE_LEDGER, List.of())
                    // No next Do — the transition into COMPLETED happens on this
                    // same success (see below). But representing it in two hops
                    // keeps each state responsible for exactly one thing.
                    : TransitionResult.of(state);

            case LEDGER_UPDATED -> TransitionResult.of(SagaState.COMPLETED);

            // Anything else (COMPENSATED / COMPENSATION_FAILED are terminal and
            // handled at the top): stay put.
            default -> TransitionResult.of(state);
        };
    }

    // ------------------------------------------------------------------------
    //  FAILURE path — start compensating
    // ------------------------------------------------------------------------

    private TransitionResult onFailure(SagaState state, List<SagaStep> done,
                                       SagaEvent.StepFailed fail) {

        // A failure during compensation is a WORSE failure — we couldn't undo
        // something. This is the "call a human" terminal state.
        if (state == SagaState.COMPENSATING) {
            return TransitionResult.of(SagaState.COMPENSATION_FAILED);
        }

        // A failure of a forward step: we've moved into COMPENSATING. Emit the
        // FIRST undo (the most-recently-completed step). Subsequent undos are
        // emitted as each Undo's StepSucceeded comes back (see advanceCompensation).
        if (done.isEmpty()) {
            // Nothing to undo — the very first step failed. We're compensated by definition.
            return TransitionResult.of(SagaState.COMPENSATED);
        }
        SagaStep lastCompleted = done.get(done.size() - 1);
        return new TransitionResult(
                SagaState.COMPENSATING,
                null,       // don't record anything on the completed_steps; we're unwinding
                List.of(new SagaCommand.Undo(fail.sagaId(), lastCompleted)));
    }

    // ------------------------------------------------------------------------
    //  COMPENSATION walk — driven by successful Undo StepSucceeded events
    // ------------------------------------------------------------------------

    /**
     * Called from the COMPENSATING state when an undo has just succeeded.
     * We logically pop the last-completed step off the stack; if any remain,
     * emit the next undo; if none, we're fully compensated.
     *
     * <p>NOTE: we do not physically mutate {@code completedSteps} here — the
     * caller (Day 17) will do the persistence pop. The service returns
     * {@code justCompleted = null} for compensation steps so the caller knows
     * to pop rather than push.
     */
    private TransitionResult advanceCompensation(UUID sagaId, List<SagaStep> done) {
        // The just-undone step is the last one in the list; the NEXT undo is
        // the one BEFORE it. If there's only one, we're done after this.
        if (done.size() <= 1) {
            return TransitionResult.of(SagaState.COMPENSATED);
        }
        SagaStep nextToUndo = done.get(done.size() - 2);
        List<SagaCommand> cmds = new ArrayList<>(1);
        cmds.add(new SagaCommand.Undo(sagaId, nextToUndo));
        return new TransitionResult(SagaState.COMPENSATING, null, cmds);
    }
}
