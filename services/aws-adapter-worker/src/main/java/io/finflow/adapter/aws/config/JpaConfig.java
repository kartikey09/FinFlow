package io.finflow.adapter.aws.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Brings the outbox-starter's persistence into this service's scan.
 *
 * <p>Sixth byte-identical copy across the codebase (two ingestors, cost-normalizer,
 * commitment-tracker, saga-orchestrator, and this). Every new outbox producer
 * proves the case louder for folding this into the library.
 */
@Configuration
@EntityScan(basePackages = {"io.finflow.adapter.aws", "io.finflow.outbox"})
@EnableJpaRepositories(basePackages = {"io.finflow.adapter.aws", "io.finflow.outbox"})
public class JpaConfig {
}
