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

    @Bean
    @ConditionalOnMissingBean
    public OutboxRetentionJob outboxRetentionJob(
            OutboxEventRepository repository,
            @Value("${finflow.outbox.retention.duration:7d}") Duration retention){
        return new OutboxRetentionJob(repository, retention);
    }
}
