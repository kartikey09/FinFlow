package io.finflow.adapter.aws.config;

import jakarta.annotation.PostConstruct;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * Runs the outbox-starter's migration on its own Flyway history timeline in
 * {@code public}. Identical to the five earlier adopters' versions.
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
