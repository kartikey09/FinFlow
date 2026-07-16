package io.finflow.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The single entry point for emitting a domain event through the outbox.
 *
 * A caller records an event by calling append(...) FROM WITHIN its own
 * @Transactional business method. Because append() participates in the caller's
 * transaction, the business change and the outbox row commit together or not at all.
 *
 * The MANDATORY propagation is the safety mechanism: it requires an already-open
 * transaction and throws if there isn't one. That makes it IMPOSSIBLE to call
 * append() outside a transaction (which would defeat the whole point by writing
 * the event non-atomically). The foot-gun is removed at the framework level.
 *
 * <h2>Day 22: events.published metric</h2>
 *
 * <p>Every append increments {@code finflow.events.published} tagged by
 * {@code aggregate_type} (which is the Kafka topic the event routes to).
 *
 * <h2>Day 24: trace context capture — the load-bearing piece of distributed tracing</h2>
 *
 * <p>FinFlow's app code NEVER produces to Kafka. It writes a row here; Debezium
 * (a separate JVM tailing the WAL) publishes. So the usual advice — "inject
 * traceparent into the Kafka producer's headers" — has nowhere to happen. The
 * trace would die at the database boundary.
 *
 * <p>The fix: capture the W3C traceparent of the CURRENT span and persist it in
 * the row, inside the same transaction as the event. Debezium's EventRouter SMT
 * then copies that column into a Kafka header named {@code traceparent}, and the
 * consumer's Spring Kafka receiver-observation extracts it with the standard W3C
 * propagator. No custom consumer code needed anywhere.
 *
 * <p>We use Micrometer's {@link Propagator} rather than hand-formatting the
 * string. That matters: the propagator emits whatever format is actually
 * configured (W3C by default with the OTel bridge, but B3 if someone switches
 * it), so the wire format can never drift out of sync with what the consumer
 * expects to parse.
 *
 * <p>Both {@code Tracer} and {@code Propagator} are OPTIONAL (nullable). A
 * service with tracing switched off, or a unit test with no tracing on the
 * classpath, appends events exactly as before with a null trace_parent — which
 * is a valid row. Tracing degrades to nothing; it never breaks the outbox.
 */
public class OutboxAppender {

    private static final String COUNTER_NAME = "finflow.events.published";
    private static final String TRACEPARENT = "traceparent";

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;

    /** Nullable — the library works without Micrometer on the classpath. */
    private final MeterRegistry meterRegistry;
    private final ConcurrentHashMap<String, Counter> countersByTopic = new ConcurrentHashMap<>();

    /** Nullable (Day 24) — the library works without Micrometer Tracing too. */
    private final Tracer tracer;
    private final Propagator propagator;

    /** Legacy 2-arg constructor kept for any code / tests that build it directly. */
    public OutboxAppender(OutboxEventRepository repository, ObjectMapper objectMapper) {
        this(repository, objectMapper, null, null, null);
    }

    /** Day 22 3-arg constructor — metrics, no tracing. Kept for compatibility. */
    public OutboxAppender(OutboxEventRepository repository, ObjectMapper objectMapper,
                          MeterRegistry meterRegistry) {
        this(repository, objectMapper, meterRegistry, null, null);
    }

    /** Day 24 full constructor. */
    public OutboxAppender(OutboxEventRepository repository, ObjectMapper objectMapper,
                          MeterRegistry meterRegistry, Tracer tracer, Propagator propagator) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        this.tracer = tracer;
        this.propagator = propagator;
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
                OffsetDateTime.now(),
                currentTraceParent());     // Day 24 — may be null, that's fine

        OutboxEvent saved = repository.save(event);
        recordPublished(aggregateType);
        return saved;
    }

    /**
     * Serialize the current span's context to a W3C traceparent string, or null
     * if there's no active span (or tracing isn't on the classpath).
     *
     * <p>We inject into a throwaway Map and pull the {@code traceparent} key out
     * of it. That's deliberately roundabout: {@link Propagator#inject} is the
     * only API that guarantees the string matches the configured propagation
     * format. Hand-building "00-" + traceId + "-" + spanId + "-01" would work
     * today and silently break the day someone switches to B3.
     */
    private String currentTraceParent() {
        if (tracer == null || propagator == null) {
            return null;
        }
        Span span = tracer.currentSpan();
        if (span == null) {
            return null;   // appended outside any span — valid, just untraced
        }
        Map<String, String> carrier = new HashMap<>(2);
        propagator.inject(span.context(), carrier, Map::put);
        return carrier.get(TRACEPARENT);
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
