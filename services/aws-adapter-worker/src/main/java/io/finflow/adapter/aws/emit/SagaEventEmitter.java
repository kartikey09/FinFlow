package io.finflow.adapter.aws.emit;

import io.finflow.outbox.OutboxAppender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Publishes result events to {@code saga.events} via the adapter's own outbox.
 *
 * <p>{@code @Transactional(MANDATORY)} — same discipline as the ingestor's
 * {@code OutboxAppender}. This ALWAYS runs inside the executor's transaction,
 * which is what guarantees: dedup row saved + event appended = both or neither.
 * If saving the dedup row succeeded but the event append somehow didn't, we'd
 * have a phantom side-effect the orchestrator never learns about — the
 * transaction boundary prevents that.
 *
 * <p>{@code aggregate_type = "saga.events"} so the saga Debezium connector
 * routes it verbatim (identity-replacement). {@code aggregate_id = sagaId} so
 * everything for one saga stays in one Kafka partition.
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
