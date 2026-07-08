package io.finflow.outbox;

import jakarta.annotation.PostConstruct;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import javax.sql.DataSource;

/**
 * Runs the outbox's Flyway migration on its OWN history timeline, in
 * {@code public}. Previously each consuming service shipped a byte-identical
 * {@code OutboxFlywayConfig} class to do exactly this.
 *
 * <h2>Why a separate Flyway invocation?</h2>
 *
 * <p>The consuming service has its own Flyway timeline pointed at
 * {@code classpath:db/migration} with a service-specific default schema. Its
 * {@code V1__baseline.sql} routinely collides (both as "V1") with the outbox
 * library's {@code V1__create_outbox_event.sql}. Rather than force every
 * service to interleave version numbers with the library, this class runs a
 * separate Flyway timeline using a different history table
 * ({@code flyway_schema_history_outbox}) and a separate migration location
 * ({@code classpath:db/outbox}).
 *
 * <p>Whichever consuming service starts first performs the migration; the
 * others see it already applied and no-op. Correct for a shared physical
 * resource ({@code public.outbox_event}).
 *
 * <h2>Opt-out</h2>
 *
 * <p>Set {@code finflow.outbox.schema.migrate=false} in a service that manages
 * the outbox schema externally (e.g. via a top-level SQL script or a
 * dedicated migration service). Default is on.
 */
@AutoConfiguration
@ConditionalOnClass(Flyway.class)
@ConditionalOnProperty(
        prefix = "finflow.outbox.schema",
        name = "migrate",
        havingValue = "true",
        matchIfMissing = true)
public class OutboxSchemaAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(OutboxSchemaAutoConfiguration.class);

    private final DataSource dataSource;

    public OutboxSchemaAutoConfiguration(DataSource dataSource) {
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
