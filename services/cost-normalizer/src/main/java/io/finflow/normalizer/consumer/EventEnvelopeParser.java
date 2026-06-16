package io.finflow.normalizer.consumer;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Turns a raw Kafka ConsumerRecord (the Outbox Event Router output) into a
 * typed ConsumedEvent.
 *
 * The event id comes from the 'id' header the router always emits for
 * deduplication; without it we cannot dedupe, so its absence is a hard error.
 * The 'eventType' header is best-effort (null-tolerant) so the consumer keeps
 * working even if that header isn't present.
 */
@Component
public class EventEnvelopeParser {

    private static final String TOPIC_PREFIX = "finflow.events.";

    public ConsumedEvent parse(ConsumerRecord<String, String> record) {
        UUID id = requireEventId(record);
        String type = headerAsString(record, "eventType");
        String aggregateType = aggregateTypeFromTopic(record.topic());
        return new ConsumedEvent(id, type, aggregateType, record.value());
    }

    private UUID requireEventId(ConsumerRecord<String, String> record) {
        Header header = record.headers().lastHeader("id");
        if (header == null || header.value() == null) {
            throw new IllegalStateException(
                    "Event on topic " + record.topic()
                            + " is missing the 'id' header; cannot deduplicate");
        }
        return UUID.fromString(unquote(new String(header.value(), StandardCharsets.UTF_8).trim()));
    }

    private String headerAsString(ConsumerRecord<String, String> record, String key) {
        Header header = record.headers().lastHeader(key);
        if (header == null || header.value() == null) {
            return null;
        }
        return unquote(new String(header.value(), StandardCharsets.UTF_8).trim());
    }

    private String aggregateTypeFromTopic(String topic) {
        return topic.startsWith(TOPIC_PREFIX) ? topic.substring(TOPIC_PREFIX.length()) : topic;
    }

    private String unquote(String value) {
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
}
