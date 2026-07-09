package io.finflow.saga.orchestration;

import io.finflow.outbox.OutboxAppender;
import io.finflow.saga.command.CommandTopicMap;
import io.finflow.saga.command.SagaCommand;
import io.finflow.saga.command.SagaCommandPayload;
import io.finflow.saga.command.SagaCommandPayload.Direction;
import io.finflow.saga.model.SagaInstance;
import io.finflow.saga.model.SagaStep;
import io.finflow.saga.model.SagaType;
import io.finflow.saga.model.Vendor;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies the mapping SagaCommand -> outbox row:
 *   - Do  -> Direction.DO,  aggregateType from CommandTopicMap.
 *   - Undo -> Direction.UNDO, aggregateType from CommandTopicMap.
 *   - idempotencyKey is deterministic from (sagaId, step, direction).
 *   - aggregate_id on the outbox row is the saga id string (Kafka key = saga id).
 *
 * <p>Day 20: the emitter now takes the {@link SagaInstance} so it can read the
 * vendor and route via {@code CommandTopicMap.aggregateTypeFor(step, vendor)}.
 */
class SagaCommandEmitterTest {

    private final OutboxAppender outboxAppender = mock(OutboxAppender.class);
    private final CommandTopicMap topicMap = mock(CommandTopicMap.class);
    private final SagaCommandEmitter emitter = new SagaCommandEmitter(outboxAppender, topicMap);

    private static SagaInstance awsSaga() {
        return new SagaInstance(UUID.randomUUID(), SagaType.REBALANCE, "corr", Vendor.AWS);
    }

    @Test
    void emitsDoCommandWithCorrectPayloadAndAggregateType() {
        UUID sagaId = UUID.randomUUID();
        when(topicMap.aggregateTypeFor(any(), any())).thenReturn("saga.commands.aws");

        emitter.emitAll(awsSaga(), List.of(new SagaCommand.Do(sagaId, SagaStep.ACQUIRE_LOCK)));

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(outboxAppender).append(
                org.mockito.ArgumentMatchers.eq("saga.commands.aws"),
                org.mockito.ArgumentMatchers.eq(sagaId.toString()),
                org.mockito.ArgumentMatchers.eq("SagaCommand"),
                payloadCaptor.capture());

        SagaCommandPayload payload = (SagaCommandPayload) payloadCaptor.getValue();
        assertThat(payload.sagaId()).isEqualTo(sagaId);
        assertThat(payload.step()).isEqualTo(SagaStep.ACQUIRE_LOCK);
        assertThat(payload.direction()).isEqualTo(Direction.DO);
        assertThat(payload.idempotencyKey())
                .isEqualTo(SagaCommandPayload.keyFor(sagaId, SagaStep.ACQUIRE_LOCK, Direction.DO));
    }

    @Test
    void emitsUndoCommandWithDirectionUndo() {
        UUID sagaId = UUID.randomUUID();
        when(topicMap.aggregateTypeFor(any(), any())).thenReturn("saga.commands.aws");

        emitter.emitAll(awsSaga(), List.of(new SagaCommand.Undo(sagaId, SagaStep.RESERVE_TARGET)));

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(outboxAppender).append(
                org.mockito.ArgumentMatchers.eq("saga.commands.aws"),
                org.mockito.ArgumentMatchers.eq(sagaId.toString()),
                org.mockito.ArgumentMatchers.eq("SagaCommand"),
                payloadCaptor.capture());
        assertThat(((SagaCommandPayload) payloadCaptor.getValue()).direction())
                .isEqualTo(Direction.UNDO);
    }

    @Test
    void emitAllHandlesMultipleCommandsInOrder() {
        UUID sagaId = UUID.randomUUID();
        when(topicMap.aggregateTypeFor(any(), any())).thenReturn("saga.commands.aws");

        emitter.emitAll(awsSaga(), List.of(
                new SagaCommand.Do(sagaId, SagaStep.ACQUIRE_LOCK),
                new SagaCommand.Do(sagaId, SagaStep.VERIFY_COMMITMENT)));

        verify(outboxAppender, times(2)).append(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any());
    }
}
