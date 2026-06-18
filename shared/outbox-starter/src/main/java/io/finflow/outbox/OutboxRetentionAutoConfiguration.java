package io.finflow.outbox;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Duration;

@AutoConfiguration
@ConditionalOnProperty(
        prefix = "finflow.outbox.retention",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
@ConditionalOnBean(OutboxEventRepository.class)
@EnableScheduling
public class OutboxRetentionAutoConfiguration {

    // tells spring how to build the OutboxRetentionJob object.
    @Bean
    // Another escape hatch. It says: "If the developer wrote their own custom
    // OutboxRetentionJob bean manually, use theirs. If they didn't, build this
    // default one." It prevents application crashes due to duplicate beans.
    @ConditionalOnMissingBean
    public OutboxRetentionJob outboxRetentionJob(  //paraemter injection
            OutboxEventRepository repository,
            @Value("${finflow.outbox.retention.duration:7d}") Duration retention){
        return new OutboxRetentionJob(repository, retention);
    }
}

// @Value("${finflow.outbox.retention.duration:7d}" line -
// Looks in the application.yml for a property defining the retention
// duration. If it doesn't find one, it defaults to "7d" (7 days).
// Spring Boot natively understands the "7d" string and converts it
// straight into a java.time.Duration object.