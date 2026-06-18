package io.finflow.normalizer;

// Imports for Testcontainers, Kafka clients, JUnit, and Awaitility
import io.debezium.testing.testcontainers.ConnectorConfiguration;
import io.debezium.testing.testcontainers.DebeziumContainer;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * What this module does:
 * This test file verifies the entire "Produce" side of the FinFlow pipeline.
 * Instead of mocking the database or Kafka, it uses a framework called
 * Testcontainers to programmatically launch real Docker containers for
 * PostgreSQL, Kafka, and Kafka Connect (Debezium).
 *
 * How it works:
 * 1. It creates an isolated Docker network.
 * 2. It boots up Postgres, Kafka, and Debezium containers.
 * 3. It manually injects a row into the Postgres outbox_event table.
 * 4. It registers the Debezium connector using the exact config from Day 8.
 * 5. It spins up a temporary Kafka Consumer and waits up to 60 seconds to see
 * if the message physically arrives in the 'finflow.events.billing' topic.
 *
 * Why we built it:
 * To completely eliminate "It works on my machine" syndrome. If this test
 * passes, it mathematically proves your database, CDC engine, and message
 * broker are wired correctly.
 */

// The @Testcontainers Annotation
// This tells the JUnit testing framework: "Hey, before you run any tests in
// this class, look for any variables annotated with @Container, boot up those
// Docker images, and when the test is done, delete them completely."
@Testcontainers
class OutboxToKafkaE2EIT {

    // Docker containers can't talk to each other by default. We create a shared
    // virtual network so Debezium can ping Postgres and Kafka using hostnames.
    private static final Network NETWORK = Network.newNetwork();

    // boots up a real confluent kafka broker on the shared network.
    @Container
    private static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"))
                    .withNetwork(NETWORK);

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(
                    DockerImageName.parse("debezium/postgres:16")
                            .asCompatibleSubstituteFor("postgres"))
                    .withNetwork(NETWORK)
                    .withNetworkAliases("postgres")
                    .withDatabaseName("finflow")
                    .withUsername("finflow")
                    .withPassword("finflow");

    @Container
    private static final DebeziumContainer CONNECT =
            new DebeziumContainer(DockerImageName.parse("quay.io/debezium/connect:2.7"))
                    .withNetwork(NETWORK)
                    .withKafka(KAFKA)
                    .dependsOn(KAFKA, POSTGRES);

    // actual test
    @Test
    void appendedOutboxRowReachesTheBillingTopic() throws Exception {
        // 1. Create the table and write a row directly to the DB.
        createOutboxTableAndInsertEvent();
        // 2. Send the REST API call to Debezium to register our connector config.
        CONNECT.registerConnector("finflow-outbox-e2e", connectorConfig());

        // 3. Listen to Kafka and verify the data arrives.
        // Distributed systems are asynchronous. If we assert instantly, it will fail
        // because Debezium takes a few milliseconds to process the WAL.
        // We use 'await()' (from the Awaitility library) to say: "Poll Kafka every
        // 500ms. If you see the 'e2e-marker' payload within 60 seconds, pass the test.
        // If 60s expires, fail the test."
        try (Consumer<String, String> consumer = newConsumer()) {
            consumer.subscribe(List.of("finflow.events.billing"));
            await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                assertThat(records.records("finflow.events.billing"))
                        .anySatisfy(record -> assertThat(record.value()).contains("e2e-marker"));
            });
        }
    }

    // 4. Database Initialization Helper
    // Because this is a fresh, blank database, Flyway hasn't run. We use standard
    // JDBC to manually create our 'outbox_event' table and insert a dummy AWS
    // billing row containing a specific JSON marker ("e2e-marker").
    private void createOutboxTableAndInsertEvent() throws Exception {
        try (Connection connection = POSTGRES.createConnection("");
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS outbox_event (
                        id             UUID PRIMARY KEY,
                        aggregate_type VARCHAR(255) NOT NULL,
                        aggregate_id   VARCHAR(255) NOT NULL,
                        type           VARCHAR(255) NOT NULL,
                        payload        JSONB        NOT NULL,
                        created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
                    )""");
            statement.execute("""
                    INSERT INTO outbox_event (id, aggregate_type, aggregate_id, type, payload)
                    VALUES ('%s', 'billing', 'aws-cur', 'RawBillingPagePulled',
                            '{"source":"e2e-marker"}'::jsonb)""".formatted(UUID.randomUUID()));
        }
    }

    // 5. The Connector Configuration
    // This is the exact equivalent of your 'outbox-connector.json' file from Day 8,
    // translated into Java so the test framework can push it to Debezium.
    // Notice snapshot.mode is "initial". Because we inserted the database row BEFORE
    // we registered the connector, Debezium must take a snapshot of existing data
    // to find it, rather than just waiting for new live streams.
    private ConnectorConfiguration connectorConfig() {
        return ConnectorConfiguration.forJdbcContainer(POSTGRES)
                .with("connector.class", "io.debezium.connector.postgresql.PostgresConnector")
                .with("topic.prefix", "finflow")
                .with("plugin.name", "pgoutput")
                .with("slot.name", "finflow_outbox_e2e_slot")
                .with("publication.name", "finflow_outbox_e2e_pub")
                .with("publication.autocreate.mode", "filtered")
                .with("table.include.list", "public.outbox_event")
                .with("snapshot.mode", "initial")
                .with("tombstones.on.delete", "false")
                .with("key.converter", "org.apache.kafka.connect.storage.StringConverter")
                .with("value.converter", "org.apache.kafka.connect.json.JsonConverter")
                .with("value.converter.schemas.enable", "false")
                // The Single Message Transform (SMT) that routes 'aggregate_type' -> Topic name
                .with("transforms", "outbox")
                .with("transforms.outbox.type", "io.debezium.transforms.outbox.EventRouter")
                .with("transforms.outbox.table.field.event.id", "id")
                .with("transforms.outbox.table.field.event.key", "aggregate_id")
                .with("transforms.outbox.table.field.event.type", "type")
                .with("transforms.outbox.table.field.event.timestamp", "created_at")
                .with("transforms.outbox.table.field.event.payload", "payload")
                .with("transforms.outbox.table.expand.json.payload", "true")
                .with("transforms.outbox.route.by.field", "aggregate_type")
                .with("transforms.outbox.route.topic.replacement", "finflow.events.${routedByValue}");
    }

    // 6. The Kafka Consumer Setup
    // A raw Java KafkaConsumer. We set AUTO_OFFSET_RESET to "earliest" to guarantee
    // that even if Debezium publishes the message before this consumer fully connects,
    // the consumer will read from the beginning of the topic and find the message.
    private Consumer<String, String> newConsumer() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "e2e-verifier");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        return new KafkaConsumer<>(props);
    }
}
