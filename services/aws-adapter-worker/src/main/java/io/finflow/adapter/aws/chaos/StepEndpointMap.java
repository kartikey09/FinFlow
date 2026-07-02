package io.finflow.adapter.aws.chaos;

import org.springframework.stereotype.Component;

/**
 * Maps a {@code SagaStep} name (received as a string on the wire) to the
 * chaos-api path segment. Kept as a single lookup so adding a step is one edit.
 *
 * <p>Undo commands intentionally hit the SAME endpoint as do commands. In a
 * real system a lock/unlock would be distinct URLs; for the demo it's the same
 * stateless call and the orchestrator's state machine tracks direction.
 * (See the note in {@code AwsCommitmentsController}.)
 */
@Component
public class StepEndpointMap {

    /**
     * @param step  the {@code SagaStep} name from the incoming command
     * @return the chaos-api action path segment (the bit after
     *         {@code /aws/commitments/{id}/}), or null for an unknown step
     */
    public String actionFor(String step) {
        if (step == null) return null;
        return switch (step) {
            case "ACQUIRE_LOCK"      -> "lock";
            case "VERIFY_COMMITMENT" -> "verify";
            case "RESERVE_TARGET"    -> "reserve";
            case "RELEASE_SOURCE"    -> "release";
            case "UPDATE_LEDGER"     -> "update-ledger";
            default                  -> null;
        };
    }
}
