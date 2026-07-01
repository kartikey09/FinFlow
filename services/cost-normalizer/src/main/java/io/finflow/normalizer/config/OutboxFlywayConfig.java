package io.finflow.normalizer.config;

import jakarta.annotation.PostConstruct;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * Runs the outbox-starter's migration on its OWN Flyway history timeline, in
 * {@code public} — exactly as both ingestors do.
 *
 * <p>Three producers now share {@code public.outbox_event} and its history
 * ({@code flyway_schema_history_outbox}). Whichever service starts first applies
 * the migration; the others see it already applied and no-op. Correct for a
 * shared resource: one physical home, one history.
 *
 * <p>Why not add {@code classpath:db/outbox} to {@code spring.flyway.locations}?
 * Its {@code V1} would collide with this service's own {@code V1__baseline} on
 * the default timeline. A separate history table keeps them independent.
 */
@Configuration
public class OutboxFlywayConfig {

    private static final Logger log = LoggerFactory.getLogger(OutboxFlywayConfig.class);

    private final DataSource dataSource;

    public OutboxFlywayConfig(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @PostConstruct
    public void migrateOutboxSchema() {
        Flyway.configure()
                .dataSource(dataSource)
                .schemas("public")
                .defaultSchema("public")
                .table("flyway_schema_history_outbox")
                .locations("classpath:db/outbox")
                .baselineOnMigrate(true)
                .load()
                .migrate();
        log.info("Outbox schema migration applied/verified (public.outbox_event)");
    }
}
