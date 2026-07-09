package io.finflow.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
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
 * <h2>Day 20 change: the library now brings its own entity + repository scan.</h2>
 *
 * <p>Before Day 20, every consuming service had to add {@code io.finflow.outbox}
 * to its own {@code @EntityScan} and {@code @EnableJpaRepositories}. Seven
 * services shipped a byte-identical {@code JpaConfig} class to do exactly that.
 *
 * <p>Now the library declares the scan once, here. There is an important
 * Spring Boot subtlety: the moment ANY {@code @EntityScan} /
 * {@code @EnableJpaRepositories} exists, Boot STOPS falling back to the
 * {@code @SpringBootApplication} package (that fallback only fires when the
 * {@code EntityScanPackages} / repository scan is otherwise empty). So a scan
 * pinned narrowly to {@code io.finflow.outbox} would silently un-scan each
 * consuming service's OWN entities and repositories — the app would fail to
 * start with "No qualifying bean of type ...Repository".
 *
 * <p>The fix is to scan the shared root {@code io.finflow}, which covers BOTH
 * {@code io.finflow.outbox} and every service's own {@code io.finflow.<svc>.*}
 * packages (a service only has its own code + this library on its classpath, so
 * the broad root never pulls in a sibling service). This is what makes the
 * per-service {@code JpaConfig} genuinely redundant and safe to delete.
 *
 * <p>The Flyway timeline for {@code public.outbox_event} is handled by
 * {@link OutboxSchemaAutoConfiguration} — separate class because it needs a
 * different set of {@code @ConditionalOn*} guards.
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
    public OutboxAppender outboxAppender(OutboxEventRepository repository, ObjectMapper objectMapper) {
        return new OutboxAppender(repository, objectMapper);
    }
}
