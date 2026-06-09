package io.finflow.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for the outbox starter. Registered via
 * META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports,
 * so any service that adds this module as a dependency gets an OutboxAppender
 * bean with no extra wiring — the Spring Boot starter idiom.
 *
 * It only contributes the appender bean. Discovery of the OutboxEvent entity
 * and OutboxEventRepository is left to the consuming service (see README): the
 * service adds io.finflow.outbox to its @EntityScan / @EnableJpaRepositories.
 * We deliberately do NOT @EntityScan here, because that would REPLACE the
 * service's own default entity scanning and break its entities.
 */
@AutoConfiguration(after = JacksonAutoConfiguration.class)
@ConditionalOnClass({ EntityManager.class, ObjectMapper.class })
public class OutboxAutoConfiguration {

    /**
     * Fallback ObjectMapper for serializing outbox payloads. A web consumer's
     * Spring-provided ObjectMapper wins via @ConditionalOnMissingBean; but a
     * JPA-only service (no spring-web, so Boot's JacksonAutoConfiguration never
     * builds one) still gets a working mapper, keeping the starter zero-wiring.
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
