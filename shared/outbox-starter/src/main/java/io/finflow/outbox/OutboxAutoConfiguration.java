package io.finflow.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Auto-configuration for the outbox starter. Registered via
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}
 * so any service that adds this module as a dependency gets an
 * {@link OutboxAppender} bean with zero wiring.
 *
 * <h2>Day 20: the library brings its own entity + repository scan.</h2>
 *
 * <p>The scan is pinned to the shared root {@code io.finflow} rather than
 * {@code io.finflow.outbox}. That's not laziness — it's required. The moment ANY
 * {@code @EntityScan} exists, Boot stops falling back to the
 * {@code @SpringBootApplication} package, so a narrow scan would silently
 * un-scan each consuming service's OWN entities. Scanning the shared root covers
 * both, and a service only ever has its own code + this library on its classpath.
 *
 * <h2>Day 22: metrics. Day 24: tracing.</h2>
 *
 * <p>The appender takes an optional {@link MeterRegistry} (to emit
 * {@code finflow.events.published}) and — new on Day 24 — an optional
 * {@link Tracer} + {@link Propagator}, used to stamp the W3C traceparent onto
 * every outbox row so the trace survives Debezium.
 *
 * <p>All three arrive via {@link ObjectProvider#getIfAvailable()}, which returns
 * null when the bean doesn't exist. So the library still works in a service with
 * no metrics, no tracing, or neither. Tracing degrades to a null column; it can
 * never break an append.
 */
@AutoConfiguration(after = JacksonAutoConfiguration.class)
@ConditionalOnClass({ EntityManager.class, ObjectMapper.class })
@EntityScan(basePackages = "io.finflow")
@EnableJpaRepositories(basePackages = "io.finflow")
public class OutboxAutoConfiguration {

    /**
     * Fallback ObjectMapper for serializing outbox payloads. A web consumer's
     * Spring-provided ObjectMapper wins via {@code @ConditionalOnMissingBean};
     * a JPA-only service (no spring-web, so Boot's JacksonAutoConfiguration
     * never builds one) still gets a working mapper.
     */
    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper outboxObjectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }

    @Bean
    @ConditionalOnMissingBean
    public OutboxAppender outboxAppender(OutboxEventRepository repository,
                                         ObjectMapper objectMapper,
                                         ObjectProvider<MeterRegistry> meterRegistry,
                                         ObjectProvider<Tracer> tracer,
                                         ObjectProvider<Propagator> propagator) {
        return new OutboxAppender(
                repository,
                objectMapper,
                meterRegistry.getIfAvailable(),
                tracer.getIfAvailable(),
                propagator.getIfAvailable());
    }
}
