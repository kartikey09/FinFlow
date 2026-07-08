package io.finflow.saga.api;

import io.finflow.saga.model.SagaInstance;
import io.finflow.saga.model.SagaState;
import io.finflow.saga.model.SagaStep;
import io.finflow.saga.model.Vendor;

import java.util.List;
import java.util.UUID;

/**
 * Response shape for both start and get. UI-shaped, deliberately flat.
 * Day 20 surfaces {@code vendor} so the dashboard can show which adapter owns
 * the saga.
 */
public record RebalanceResponse(
        UUID id,
        String correlationId,
        Vendor vendor,
        SagaState currentState,
        List<SagaStep> completedSteps
) {
    public static RebalanceResponse of(SagaInstance saga) {
        return new RebalanceResponse(
                saga.getId(),
                saga.getCorrelationId(),
                saga.getVendor(),
                saga.getCurrentState(),
                saga.getCompletedSteps());
    }
}
