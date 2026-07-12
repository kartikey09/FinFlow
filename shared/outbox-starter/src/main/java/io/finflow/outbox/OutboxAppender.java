package io.finflow.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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
 *
 * <h2>Day 22: events.published metric</h2>
 *
 * <p>Every append increments {@code finflow.events.published} tagged by
 * {@code aggregate_type} (which is the Kafka topic the event routes to). The
 * MeterRegistry is optional — if none is on the classpath the appender behaves
 * exactly as before. Counters are cached per aggregate_type so we don't rebuild
 * the meter on every call.
 *
 * <p>The counter increments AFTER {@code repository.save} returns, so a
 * serialization failure or a rolled-back transaction won't inflate it. (Strictly,
 * a transaction that rolls back AFTER save still counts here — but Micrometer
 * counters are monotonic and this is a "how many events did we hand to the
 * outbox" signal, not a "how many committed" signal. The DLT/consumer metrics
 * cover the delivered side.)
 */
public class OutboxAppender {

    private static final String COUNTER_NAME = "finflow.events.published";

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;

    /** Nullable — the library works without Micrometer on the classpath. */
    private final MeterRegistry meterRegistry;
    private final ConcurrentHashMap<String, Counter> countersByTopic = new ConcurrentHashMap<>();

    /** Legacy 2-arg constructor kept for any code / tests that build it directly. */
    public OutboxAppender(OutboxEventRepository repository, ObjectMapper objectMapper) {
        this(repository, objectMapper, null);
    }

    public OutboxAppender(OutboxEventRepository repository, ObjectMapper objectMapper,
                          MeterRegistry meterRegistry) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
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

        OutboxEvent saved = repository.save(event);
        recordPublished(aggregateType);
        return saved;
    }

    private void recordPublished(String aggregateType) {
        if (meterRegistry == null) {
            return;
        }
        countersByTopic.computeIfAbsent(aggregateType, topic ->
                Counter.builder(COUNTER_NAME)
                        .description("Domain events appended to the outbox, by aggregate type (= Kafka topic)")
                        .tag("topic", topic)
                        .register(meterRegistry)
        ).increment();
    }
}


//what it does and where. A tiny unchecked RuntimeException, thrown by the appender when Jackson fails to serialize a
//payload. The fact that it's unchecked is the whole point: Spring's @Transactional rolls back automatically on
//RuntimeException and Error, but not on checked exceptions. So by making this unchecked, a serialization failure rolls
//the entire transaction back — the business change won't sneak through committed while its event silently failed to
//record. It's used only inside the appender's catch block, but its behavior (triggering rollback) is part of the
//atomicity guarantee.
