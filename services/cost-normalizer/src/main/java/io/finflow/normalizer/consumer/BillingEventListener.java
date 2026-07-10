package io.finflow.normalizer.consumer;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Subscribes to every CDC event topic (finflow.events.*) via a topic pattern,
 * so new aggregate-type topics are picked up automatically.
 *
 * Uses MANUAL acknowledgment: the offset is committed only after the event is
 * fully handled and its dedup record is durable. A crash mid-processing leaves
 * the offset uncommitted, so the event is redelivered rather than dropped — and
 * the dedup ledger makes that redelivery a no-op.
 *
 * <h2>Day 22: finflow.events.consumed</h2>
 *
 * <p>Increments {@code finflow.events.consumed} tagged by {@code topic} (the
 * actual Kafka topic the record came from) and {@code outcome} (success|failure).
 * The counter is bumped AFTER handling: success before the ack, failure in a
 * catch that then rethrows so Kafka still redelivers (the metric records the
 * attempt, not a swallow). This pairs with {@code finflow.events.published} on
 * the producer side — together they let the dashboard show end-to-end flow and
 * spot a consumer falling behind or erroring.
 */
@Component
public class BillingEventListener {

    private static final String METRIC = "finflow.events.consumed";

    private final EventEnvelopeParser parser;
    private final BillingEventHandler handler;
    private final MeterRegistry meterRegistry;
    private final ConcurrentHashMap<String, Counter> counters = new ConcurrentHashMap<>();

    public BillingEventListener(EventEnvelopeParser parser, BillingEventHandler handler,
                                MeterRegistry meterRegistry) {
        this.parser = parser;
        this.handler = handler;
        this.meterRegistry = meterRegistry;
    }

    @KafkaListener(topicPattern = "finflow\\.events\\..*")
    public void onMessage(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        try {
            ConsumedEvent event = parser.parse(record);
            handler.handle(event);
            count(record.topic(), "success");
            acknowledgment.acknowledge();
        } catch (RuntimeException e) {
            count(record.topic(), "failure");
            throw e;   // let Kafka redeliver; the dedup ledger makes retries safe
        }
    }

    private void count(String topic, String outcome) {
        if (meterRegistry == null) return;
        counters.computeIfAbsent(topic + "|" + outcome, key ->
                Counter.builder(METRIC)
                        .description("Events consumed from Kafka, by topic and outcome")
                        .tag("topic", topic)
                        .tag("outcome", outcome)
                        .register(meterRegistry)
        ).increment();
    }
}
