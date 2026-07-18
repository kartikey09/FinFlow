package io.finflow.recommendation.consume;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Reads the Debezium/outbox envelope headers off a Kafka record: {@code eventType}
 * (which kind of event) and {@code id} (the event UUID, for idempotency).
 *
 * <p>The {@code unquote} step matters. Kafka Connect's default header converter wraps
 * string header values in double quotes; every FinFlow consumer strips them defensively
 * (no-op when they're absent). This centralizes that logic instead of copy-pasting it
 * into each listener, as query-api currently does.
 */
public final class EventEnvelope {

    private EventEnvelope() {}

    public static String type(ConsumerRecord<String, String> record) {
        return header(record, "eventType");
    }

    public static UUID id(ConsumerRecord<String, String> record) {
        String raw = header(record, "id");
        if (raw == null) return null;
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static String header(ConsumerRecord<String, String> record, String name) {
        Header h = record.headers().lastHeader(name);
        if (h == null || h.value() == null) return null;
        String raw = new String(h.value(), StandardCharsets.UTF_8).trim();
        if (raw.length() >= 2 && raw.startsWith("\"") && raw.endsWith("\"")) {
            raw = raw.substring(1, raw.length() - 1);
        }
        return raw;
    }
}
