package io.finflow.chaosapi.aws.dto;

/**
 * Response body from the AWS commitments rebalance endpoints.
 *
 * <p>Deliberately small: the adapter workers only care whether the action
 * succeeded. Business state (lock owner, reservation id, etc.) would live on
 * a real AWS control-plane API; here the chaos-api is a stand-in whose
 * SOLE responsibility is either respond 200 with {@code success=true} or —
 * when the {@code ChaosInterceptor} decides — a 503 or a hang.
 */
public record CommitmentActionResponse(
        boolean success,
        String commitmentId,
        String action
) {}
