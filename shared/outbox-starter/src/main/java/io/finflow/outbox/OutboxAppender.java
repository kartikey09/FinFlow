package io.finflow.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * The single entry point for emitting a domain event through the outbox.
 *
 * A caller records an event by calling append(...) FROM WITHIN its own
 * @Transactional business method, e.g.:
 *
 *   @Transactional
 *   public void processPage(...) {
 *       pollCursorRepository.save(advancedCursor);          // business change
 *       outboxAppender.append("billing", sourceId,          // event, same tx
 *                             "RawBillingPagePulled", page);
 *   }
 *
 * Because append() participates in the caller's transaction, the cursor update
 * and the outbox row commit together or not at all.
 *
 * The MANDATORY propagation is the safety mechanism: it requires an already-open
 * transaction and throws if there isn't one. That makes it IMPOSSIBLE to call
 * append() outside a transaction (which would defeat the whole point by writing
 * the event non-atomically). The foot-gun is removed at the framework level.
 */

public class OutboxAppender {

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;

    public OutboxAppender(OutboxEventRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public OutboxEvent append(String aggregateType, String aggregateId,
                              String type, Object payload) {
        final String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new OutboxException("Failed to serialize outbox payload for type=" + type, e);
        }

        OutboxEvent event = new OutboxEvent(
                UUID.randomUUID(),
                aggregateType,
                aggregateId,
                type,
                json,
                OffsetDateTime.now());

        return repository.save(event);
    }
}


//what it does and where. A tiny unchecked RuntimeException, thrown by the appender when Jackson fails to serialize a
//payload. The fact that it's unchecked is the whole point: Spring's @Transactional rolls back automatically on
//RuntimeException and Error, but not on checked exceptions. So by making this unchecked, a serialization failure rolls
//the entire transaction back — the business change won't sneak through committed while its event silently failed to
//record. It's used only inside the appender's catch block, but its behavior (triggering rollback) is part of the
//atomicity guarantee.
