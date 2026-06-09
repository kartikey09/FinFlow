package io.finflow.outbox;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves the three guarantees that ARE the outbox contract, against a real
 * Postgres (Testcontainers). Flyway runs the shipped V1 migration, so this also
 * validates the production DDL matches the entity.
 */
@SpringBootTest
@Testcontainers
class OutboxAppenderIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired OutboxAppender appender;
    @Autowired OutboxEventRepository repository;
    @Autowired PlatformTransactionManager txManager;

    private TransactionTemplate tx;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(txManager);
        repository.deleteAll();
    }

    @Test
    void append_withinTransaction_persistsTheEvent() {
        tx.executeWithoutResult(status ->
                appender.append("rebalance", "agg-1", "RebalanceRequested",
                        Map.of("amount", 10, "currency", "USD")));

        assertThat(repository.count()).isEqualTo(1);
        OutboxEvent saved = repository.findAll().get(0);
        assertThat(saved.getAggregateType()).isEqualTo("rebalance");
        assertThat(saved.getAggregateId()).isEqualTo("agg-1");
        assertThat(saved.getType()).isEqualTo("RebalanceRequested");
        assertThat(saved.getPayload()).contains("amount").contains("USD"); // stored as jsonb
    }

    @Test
    void append_whenSurroundingTransactionRollsBack_persistsNothing() {
        // The business transaction fails AFTER appending — the event must vanish with it.
        assertThatThrownBy(() ->
                tx.executeWithoutResult(status -> {
                    appender.append("rebalance", "agg-2", "RebalanceRequested", Map.of("k", "v"));
                    throw new RuntimeException("business failure after append");
                }))
                .isInstanceOf(RuntimeException.class);

        assertThat(repository.count()).isZero(); // atomic: rolled back together
    }

    @Test
    void append_withoutAnActiveTransaction_isRejected() {
        // MANDATORY propagation forbids appending outside a transaction.
        assertThatThrownBy(() ->
                appender.append("rebalance", "agg-3", "RebalanceRequested", Map.of()))
                .isInstanceOf(IllegalTransactionStateException.class);
    }
}
