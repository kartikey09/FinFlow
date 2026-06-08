package io.finflow.ingestion.aws;

import io.finflow.ingestion.aws.cursor.PollCursor;
import io.finflow.ingestion.aws.cursor.PollCursorRepository;
import io.finflow.ingestion.aws.smoke.SmokeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Week-1 proof that the shell genuinely talks to real Postgres and Kafka.
 *
 * @ServiceConnection (Spring Boot 3.1+) auto-wires the datasource and Kafka
 * bootstrap to the started containers — no manual property plumbing. Flyway
 * then runs V1 inside the container DB, and the round-trip exercises the full
 * produce→consume→persist path against real infrastructure.
 *
 * The chaos-api isn't running in this test, so its health indicator would be
 * DOWN; we point it at a dead port and assert on the pieces under test rather
 * than overall health.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class AwsIngestorSmokeIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine").withDatabaseName("finflow");

    @Container
    @ServiceConnection
    static KafkaContainer kafka =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        // no chaos-api in this test — a closed port keeps its health check fast
        registry.add("finflow.chaos-api.base-url", () -> "http://localhost:59999");
    }

    @Autowired PollCursorRepository cursors;
    @Autowired SmokeService smokeService;

    @Test
    void flywayRan_andCursorTableIsUsable() {
        PollCursor c = new PollCursor("it-source");
        c.setLastToken("tok-1");
        c.setUpdatedAt(OffsetDateTime.now());
        cursors.save(c);

        assertThat(cursors.findById("it-source")).isPresent();
        assertThat(cursors.findById("it-source").get().getLastToken()).isEqualTo("tok-1");
    }

    @Test
    void kafkaRoundTrip_succeeds_andPersistsCursor() throws Exception {
        long ms = smokeService.publishAndAwait("it-payload");

        assertThat(ms).isGreaterThanOrEqualTo(0);
        // the smoke service upserts the "aws-cur" cursor as part of the round-trip
        assertThat(cursors.findById("aws-cur")).isPresent();
    }
}
