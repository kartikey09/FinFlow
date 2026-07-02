package io.finflow.saga.orchestration;

import io.finflow.outbox.OutboxAppender;
import io.finflow.saga.command.CommandTopicMap;
import io.finflow.saga.command.SagaCommand;
import io.finflow.saga.command.SagaCommandPayload;
import io.finflow.saga.command.SagaCommandPayload.Direction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Converts pure-Java {@link SagaCommand}s from {@code SagaTransitionService}
 * into outbox appends so Debezium can publish them.
 *
 * <p>{@code @Transactional(MANDATORY)} — this always runs INSIDE the caller's
 * transaction (either the REST-endpoint transaction that started the saga, or
 * the Kafka listener transaction that transitioned it). That guarantee is the
 * whole point: the SagaInstance save and the outbox append happen or fail
 * together. No possibility of state changing without the command being emitted,
 * or vice versa.
 *
 * <p>The {@code aggregate_id} on the outbox row is the saga id (as a string).
 * The Kafka key ends up being the saga id — so all commands and events for one
 * saga stay ordered within a partition.
 */
@Service
public class SagaCommandEmitter {

    private static final Logger log = LoggerFactory.getLogger(SagaCommandEmitter.class);

    private final OutboxAppender outboxAppender;
    private final CommandTopicMap topicMap;

    public SagaCommandEmitter(OutboxAppender outboxAppender, CommandTopicMap topicMap) {
        this.outboxAppender = outboxAppender;
        this.topicMap = topicMap;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void emitAll(List<SagaCommand> commands) {
        for (SagaCommand command : commands) {
            emit(command);
        }
    }

    private void emit(SagaCommand command) {
        Direction direction = switch (command) {
            case SagaCommand.Do   ignored -> Direction.DO;
            case SagaCommand.Undo ignored -> Direction.UNDO;
        };
        String aggregateType = topicMap.aggregateTypeFor(command.step());
        String idempotencyKey = SagaCommandPayload.keyFor(command.sagaId(), command.step(), direction);
        SagaCommandPayload payload = new SagaCommandPayload(
                command.sagaId(), command.step(), direction, idempotencyKey);

        outboxAppender.append(
                aggregateType,
                command.sagaId().toString(),
                "SagaCommand",
                payload);
        log.info("Emitted {} {} for saga {} (topic {}, idempotencyKey {})",
                direction, command.step(), command.sagaId(), aggregateType, idempotencyKey);
    }
}
