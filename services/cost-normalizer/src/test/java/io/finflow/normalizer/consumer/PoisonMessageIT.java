package io.finflow.normalizer.consumer;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Proves the consumer-side dead-letter path WITHOUT Docker.
 *
 * <p>An in-JVM broker ({@code @EmbeddedKafka}) drives the Kafka side; the
 * datasource is a real Postgres via Testcontainers. The poison record fails at
 * parse time, before the handler ever touches the database, so the DB only
 * exists to let the full context start — but it must be Postgres, not H2,
 * because the shared outbox migration ({@code OutboxFlywayConfig}) uses the
 * Postgres-only {@code JSONB} type, which H2 cannot parse.
 *
 * <p>Scenario: produce an event with <b>no {@code id} header</b>.
 * {@code EventEnvelopeParser} throws {@link IllegalStateException};
 * {@code KafkaErrorHandlingConfig} has marked that non-retryable, so the record
 * is republished straight to {@code finflow.events.billing.DLT}. The assertion
 * is that it lands there — i.e. the bad event is quarantined, not retried
 * forever and not silently dropped.
 */
@SpringBootTest(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.consumer.group-id=poison-test-group"
})
@EmbeddedKafka(partitions = 1, topics = {"finflow.events.billing", "finflow.events.billing.DLT"})
@Testcontainers
class PoisonMessageIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final String SOURCE_TOPIC = "finflow.events.billing";
    private static final String DLT_TOPIC = "finflow.events.billing.DLT";

    @Autowired
    private EmbeddedKafkaBroker broker;

    @Test
    void poisonEventIsRoutedToTheDeadLetterTopic() {
        publishPoisonRecord();

        String deadLettered = awaitFirstDltRecord();

        assertThat(deadLettered).contains("poison");
    }

    /** A record with no 'id' header — guaranteed to fail in the parser. */
    private void publishPoisonRecord() {
        Map<String, Object> props = KafkaTestUtils.producerProps(broker);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        try (Producer<String, String> producer = new KafkaProducer<>(props)) {
            producer.send(new ProducerRecord<>(SOURCE_TOPIC, "agg-1", "{\"poison\":true}"));
            producer.flush();
        }
    }

    private String awaitFirstDltRecord() {
        Map<String, Object> props = KafkaTestUtils.consumerProps("dlt-verifier", "true", broker);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        AtomicReference<String> found = new AtomicReference<>();
        try (Consumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of(DLT_TOPIC));
            await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                for (ConsumerRecord<String, String> record : records.records(DLT_TOPIC)) {
                    found.set(record.value());
                }
                assertThat(found.get()).isNotNull();
            });
        }
        return found.get();
    }
}
