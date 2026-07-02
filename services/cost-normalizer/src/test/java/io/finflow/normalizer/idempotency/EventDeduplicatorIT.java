package io.finflow.normalizer.idempotency;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import io.finflow.normalizer.config.DedupCacheConfig;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the dedup guard against a real Postgres: an id is unseen until marked,
 * seen afterwards, and ids are tracked independently. @DataJpaTest wraps each
 * test in a transaction (rolled back after) which also satisfies the
 * EventDeduplicator's MANDATORY propagation. Needs Docker (Testcontainers).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import({EventDeduplicator.class, DedupCacheConfig.class})
class EventDeduplicatorIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    EventDeduplicator deduplicator;

    @Test
    void detectsAnEventOnlyAfterItIsMarked() {
        UUID id = UUID.randomUUID();

        assertThat(deduplicator.alreadyProcessed(id)).isFalse();
        deduplicator.markProcessed(id, "RawBillingPagePulled");
        assertThat(deduplicator.alreadyProcessed(id)).isTrue();
    }

    @Test
    void tracksDifferentEventsIndependently() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();

        deduplicator.markProcessed(a, "E");

        assertThat(deduplicator.alreadyProcessed(a)).isTrue();
        assertThat(deduplicator.alreadyProcessed(b)).isFalse();
    }
}
