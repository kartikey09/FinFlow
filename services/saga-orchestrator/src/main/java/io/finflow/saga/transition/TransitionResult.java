package io.finflow.saga.transition;

import io.finflow.saga.command.SagaCommand;
import io.finflow.saga.model.SagaState;
import io.finflow.saga.model.SagaStep;

import java.util.List;

/**
 * The pure output of {@link SagaTransitionService#evaluate}:
 *
 * <ul>
 *   <li>{@code nextState} — where the saga should move to.</li>
 *   <li>{@code justCompleted} — the step (if any) whose completion drove this
 *       transition. Appended to {@code SagaInstance.completedSteps} by the
 *       caller so compensation knows what to undo.</li>
 *   <li>{@code commandsToEmit} — 0..N commands the caller should append to the
 *       outbox in the same transaction as the state save. Empty for terminal
 *       transitions; typically one command per hop; possibly a list of undos
 *       when starting compensation.</li>
 * </ul>
 *
 * <p>Kept as a record on purpose: the transition service is a pure function
 * (state, event) → this, and a record makes that shape stare you in the face.
 */
public record TransitionResult(
        SagaState nextState,
        SagaStep justCompleted,          // may be null (e.g. entering STARTED or compensation)
        List<SagaCommand> commandsToEmit
) {
    public static TransitionResult of(SagaState nextState) {
        return new TransitionResult(nextState, null, List.of());
    }
    public static TransitionResult of(SagaState nextState, SagaStep completed, SagaCommand next) {
        return new TransitionResult(nextState, completed, List.of(next));
    }
    public static TransitionResult of(SagaState nextState, SagaStep completed, List<SagaCommand> commands) {
        return new TransitionResult(nextState, completed, List.copyOf(commands));
    }
}
