package io.finflow.saga.api;

import io.finflow.saga.model.SagaInstance;
import io.finflow.saga.model.SagaState;
import io.finflow.saga.model.SagaStep;

import java.util.List;
import java.util.UUID;

/**
 * Response shape for both start and get. UI-shaped, deliberately flat.
 */
public record RebalanceResponse(
        UUID id,
        String correlationId,
        SagaState currentState,
        List<SagaStep> completedSteps
) {
    public static RebalanceResponse of(SagaInstance saga) {
        return new RebalanceResponse(
                saga.getId(),
                saga.getCorrelationId(),
                saga.getCurrentState(),
                saga.getCompletedSteps());
    }
}
