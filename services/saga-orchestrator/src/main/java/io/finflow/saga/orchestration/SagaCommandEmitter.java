package io.finflow.saga.orchestration;

import io.finflow.outbox.OutboxAppender;
import io.finflow.saga.command.CommandTopicMap;
import io.finflow.saga.command.SagaCommand;
import io.finflow.saga.command.SagaCommandPayload;
import io.finflow.saga.command.SagaCommandPayload.Direction;
import io.finflow.saga.model.SagaInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Converts pure-Java {@link SagaCommand}s into outbox appends.
 *
 * <p>Day 20 change: the emitter now takes the {@link SagaInstance} alongside
 * the commands, so it can read the vendor and route to the right topic. This
 * closes the "AWS-only" hard-coding from Day 17.
 *
 * <p>{@code @Transactional(MANDATORY)} — always runs inside the caller's
 * transaction. That's the atomicity guarantee: SagaInstance save and outbox
 * append commit together, or roll back together.
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
    public void emitAll(SagaInstance saga, List<SagaCommand> commands) {
        for (SagaCommand command : commands) {
            emit(saga, command);
        }
    }

    private void emit(SagaInstance saga, SagaCommand command) {
        Direction direction = switch (command) {
            case SagaCommand.Do   ignored -> Direction.DO;
            case SagaCommand.Undo ignored -> Direction.UNDO;
        };
        String aggregateType = topicMap.aggregateTypeFor(command.step(), saga.getVendor());
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
