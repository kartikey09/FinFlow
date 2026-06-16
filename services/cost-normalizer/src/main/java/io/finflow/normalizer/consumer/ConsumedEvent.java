package io.finflow.normalizer.consumer;

import java.util.UUID;

/**
 * A domain event received from Kafka, unwrapped from the Debezium Outbox Event
 * Router envelope:
 *   id            - the outbox event id (from the 'id' Kafka header) — dedup key
 *   type          - the event type (from the 'eventType' header; may be null)
 *   aggregateType - derived from the topic name (finflow.events.&lt;aggregateType&gt;)
 *   payload       - the raw JSON event body (the Kafka message value)
 */
public record ConsumedEvent(
        UUID id,
        String type,
        String aggregateType,
        String payload) {
}
