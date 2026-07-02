package io.finflow.saga.command;

import io.finflow.saga.model.SagaStep;

import java.util.UUID;

/**
 * The JSON body written to the outbox for every outbound saga command.
 *
 * <p>Deliberately kept SMALL. The adapter workers (Days 18–19) receive one of
 * these per Kafka record and need three things:
 * <ul>
 *   <li>{@code sagaId} — to correlate their result event back to the right
 *       saga (echoed straight into the {@code saga.events} response).</li>
 *   <li>{@code step} — which forward or reverse operation to perform.</li>
 *   <li>{@code direction} — {@code DO} or {@code UNDO}; the same step in both
 *       directions is what compensation exploits.</li>
 *   <li>{@code idempotencyKey} — deterministic value (sagaId + step + direction)
 *       so that if the adapter receives the same command twice (redelivery,
 *       adapter crash after side-effect but before ack), the second execution
 *       is a no-op. Day 18 will maintain a small dedup table keyed on this.</li>
 * </ul>
 *
 * <p>What this record does NOT carry: the specific commitment ids, target /
 * source ARNs, tenant, etc. Those belong on the {@code SagaInstance} row (Day
 * 18 will add a small {@code payload} column) and the adapter reads them there
 * via a companion "get saga context" endpoint or joins them from the saga row
 * directly. Keeping the Kafka command tight means every replay is cheap and
 * every log line is readable.
 */
public record SagaCommandPayload(
        UUID sagaId,
        SagaStep step,
        Direction direction,
        String idempotencyKey
) {
    public enum Direction { DO, UNDO }

    /** Idempotency key: deterministic from (sagaId, step, direction). */
    public static String keyFor(UUID sagaId, SagaStep step, Direction direction) {
        return sagaId + ":" + step + ":" + direction;
    }
}
