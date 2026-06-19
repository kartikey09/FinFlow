package io.finflow.ingestion.aws.config;

import jakarta.annotation.PostConstruct;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * Runs the outbox-starter's migration ({@code classpath:db/outbox}) on its OWN
 * Flyway history timeline, in the {@code public} schema.
 *
 * <p><b>Why not just add {@code classpath:db/outbox} to spring.flyway.locations?</b>
 * Because the library's migration is {@code V1__create_outbox_event.sql} and this
 * service already has {@code V1__baseline.sql} (applied on Day 5). Flyway flattens
 * every location into a single version timeline and rejects two migrations sharing
 * version {@code 1} — the app wouldn't start. Giving the outbox its own history
 * table ({@code flyway_schema_history_outbox}) keeps the two {@code V1}s on
 * independent timelines, so there is no collision.
 *
 * <p><b>Why {@code public}?</b> Debezium tails {@code public.outbox_event}; the
 * shared outbox is not service-private. {@code baselineOnMigrate} is on so this
 * coexists with the dev bootstrap ({@code postgres-init/01-outbox.sql}), which may
 * have already created the table on a fresh volume.
 *
 * <p>Runs in {@code @PostConstruct} (not as a {@code Flyway} bean) on purpose: a
 * {@code Flyway} bean would switch off Spring Boot's auto-configured Flyway, which
 * still owns this service's own {@code ingestion} migrations. The two are
 * independent — different schemas, different history tables.
 */

@Configuration
public class OutboxFlywayConfig {
    private static final Logger log = LoggerFactory.getLogger(OutboxFlywayConfig.class);

    private final DataSource dataSource;

    public OutboxFlywayConfig(DataSource dataSource){
        this.dataSource = dataSource;
    }

    // `@PostConstruct` means: "Once this class is built and the database connection
    // is ready, run this method exactly once." It doesn't interfere with Spring.
    @PostConstruct
    public void migrateOutboxSchema(){
        // We bypass Spring's application.yml and build a Flyway instance purely in Java code.
        Flyway.configure()
                .dataSource(dataSource)
                // We force the outbox into the 'public' schema. Why? Because
                // Debezium is configured to look in 'public' by default. If we
                // buried it inside a private 'ingestion' schema, the CDC connector
                // would require much more complex routing logic.
                .schemas("public")
                .defaultSchema("public")
                //Instead of using the default
                // 'flyway_schema_history' table, we tell it to write its version
                // logs into 'flyway_schema_history_outbox'. This creates an
                // isolated, parallel timeline for the shared library.
                .table("flyway_schema_history_outbox")               // independent timeline -> no V1 clash
                .locations("classpath:db/outbox")
                .baselineOnMigrate(true)                             // coexist with the dev-init table
                // Load the configuration and fire the SQL to the database.
                .load()
                .migrate();
        log.info("Outbox schema migration applied (public.outbox_event)");
    }
}

/// `baselineOnMigrate(true)` tells Flyway-
// "If the table already exists, don't crash. Just mark V1 as 'done' and move on."
// To run and check Debezium we made a local script that manually created the outbox table
// so it alrady exists thats why this is set to true.