package io.finflow.saga.it;

import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class for saga integration tests. Shared containers keep test time down;
 * each test uses its own correlation ID + saga ID for isolation.
 *
 * <p>Runs against real Postgres + Kafka via Testcontainers. REQUIRES Docker on
 * the host — that's the blocker for the MacBook Air; the parallel *Test
 * classes prove the same properties without Docker.
 */
public abstract class SagaITBase {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("finflow")
            .withUsername("finflow")
            .withPassword("finflow")
            .withReuse(true);

    static final KafkaContainer KAFKA = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.5.0"))
            .withReuse(true);

    static {
        POSTGRES.start();
        KAFKA.start();
    }

    /**
     * Property initializer that wires Testcontainers URLs into the Spring
     * context. Consuming ITs use
     *   {@code @ContextConfiguration(initializers = TestPropertyInitializer.class)}
     * to make this apply.
     */
    public static class TestPropertyInitializer
            implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext ctx) {
            TestPropertyValues.of(
                    "spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                    "spring.datasource.username=" + POSTGRES.getUsername(),
                    "spring.datasource.password=" + POSTGRES.getPassword(),
                    "spring.kafka.bootstrap-servers=" + KAFKA.getBootstrapServers()
            ).applyTo(ctx.getEnvironment());
        }
    }
}
