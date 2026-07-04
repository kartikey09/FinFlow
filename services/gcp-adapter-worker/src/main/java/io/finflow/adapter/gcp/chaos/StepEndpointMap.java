package io.finflow.adapter.gcp.chaos;

import org.springframework.stereotype.Component;

/**
 * SagaStep → chaos-api action path segment. Identical mapping to
 * aws-adapter-worker's version — the saga steps are vendor-agnostic, the
 * action names happen to be the same because chaos-api mirrors both sides.
 *
 * <p>If GCP's real control-plane API ever needed different endpoint names,
 * this would be where they diverge. Kept as a lookup so any future divergence
 * is one edit.
 */
@Component
public class StepEndpointMap {

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
