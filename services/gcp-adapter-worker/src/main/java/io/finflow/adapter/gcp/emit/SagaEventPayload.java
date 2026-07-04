package io.finflow.adapter.gcp.emit;

import java.util.UUID;

/**
 * The wire shape published to {@code saga.events}. IDENTICAL to
 * aws-adapter-worker's SagaEventPayload, but owned by this service — the same
 * "adapter models its own contract" discipline. If the AWS adapter changes
 * its shape, this one is unaffected; if the orchestrator changes what it
 * accepts, both adapters would need to update independently.
 */
public record SagaEventPayload(
        UUID sagaId,
        String step,
        boolean success,
        String reason
) {}
