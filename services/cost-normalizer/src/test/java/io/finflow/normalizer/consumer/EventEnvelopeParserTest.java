package io.finflow.normalizer.consumer;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pure unit test of the envelope parser — header extraction, topic-derived
 * aggregate type, and the hard requirement of an 'id' header. No Docker, no
 * Spring, runs instantly.
 */
class EventEnvelopeParserTest {

    private final EventEnvelopeParser parser = new EventEnvelopeParser();

    @Test
    void parsesIdTypeAggregateTypeAndPayload() {
        UUID id = UUID.randomUUID();
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "finflow.events.billing", 0, 0L, "aws-cur", "{\"source\":\"aws-cur\"}");
        record.headers().add("id", id.toString().getBytes(StandardCharsets.UTF_8));
        record.headers().add("eventType", "RawBillingPagePulled".getBytes(StandardCharsets.UTF_8));

        ConsumedEvent event = parser.parse(record);

        assertThat(event.id()).isEqualTo(id);
        assertThat(event.type()).isEqualTo("RawBillingPagePulled");
        assertThat(event.aggregateType()).isEqualTo("billing");
        assertThat(event.payload()).contains("aws-cur");
    }

    @Test
    void toleratesAMissingEventTypeHeader() {
        UUID id = UUID.randomUUID();
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "finflow.events.commitment", 0, 0L, "cud-1", "{}");
        record.headers().add("id", id.toString().getBytes(StandardCharsets.UTF_8));

        ConsumedEvent event = parser.parse(record);

        assertThat(event.id()).isEqualTo(id);
        assertThat(event.type()).isNull();
        assertThat(event.aggregateType()).isEqualTo("commitment");
    }

    @Test
    void rejectsAMessageWithoutAnIdHeader() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "finflow.events.billing", 0, 0L, "k", "{}");

        assertThatThrownBy(() -> parser.parse(record))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("id");
    }
}
