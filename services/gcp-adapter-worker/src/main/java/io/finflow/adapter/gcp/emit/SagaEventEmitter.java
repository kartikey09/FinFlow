package io.finflow.adapter.gcp.emit;

import io.finflow.outbox.OutboxAppender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Publishes result events to {@code saga.events} via this adapter's outbox.
 *
 * <p>The topic is SAME as aws-adapter-worker uses — per the plan: "the
 * orchestrator doesn't care which vendor responded; it identifies sagas by
 * correlation ID." The orchestrator's Kafka listener consumes {@code saga.events}
 * and the state machine advances regardless of which adapter posted.
 *
 * <p>{@code @Transactional(MANDATORY)} — this ALWAYS runs inside the executor's
 * transaction, guaranteeing dedup row and outbox event commit together.
 */
@Service
public class SagaEventEmitter {

    private final OutboxAppender outboxAppender;

    public SagaEventEmitter(OutboxAppender outboxAppender) {
        this.outboxAppender = outboxAppender;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(UUID sagaId, String step, boolean success, String reason) {
        String type = success ? "StepSucceeded" : "StepFailed";
        outboxAppender.append(
                "saga.events",
                sagaId.toString(),
                type,
                new SagaEventPayload(sagaId, step, success, reason));
    }
}
