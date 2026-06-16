package io.finflow.normalizer.consumer;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Subscribes to every CDC event topic (finflow.events.*) via a topic pattern,
 * so new aggregate-type topics are picked up automatically.
 *
 * Uses MANUAL acknowledgment: the offset is committed only after the event is
 * fully handled and its dedup record is durable. A crash mid-processing leaves
 * the offset uncommitted, so the event is redelivered rather than dropped — and
 * the dedup ledger makes that redelivery a no-op.
 */
@Component
public class BillingEventListener {

    private final EventEnvelopeParser parser;
    private final BillingEventHandler handler;

    public BillingEventListener(EventEnvelopeParser parser, BillingEventHandler handler) {
        this.parser = parser;
        this.handler = handler;
    }

    @KafkaListener(topicPattern = "finflow\\.events\\..*")
    public void onMessage(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        ConsumedEvent event = parser.parse(record);
        handler.handle(event);
        acknowledgment.acknowledge();
    }
}
