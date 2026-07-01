package io.finflow.normalizer.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Brings the outbox-starter's persistence into this service's scan, alongside
 * the normalizer's own entities/repositories.
 *
 * <p>Without {@code io.finflow.outbox} on these lists, the auto-configured
 * {@code OutboxAppender} bean is never created (it's gated on
 * {@code OutboxEventRepository} existing). Same boilerplate as both ingestors —
 * the third time it's appeared, which is the cue that it belongs IN the
 * outbox-starter as a library-side @ConfigurationProperties-driven scan. Kept
 * here for now to respect the starter's locked contract; see DAY13-MANIFEST.
 */
@Configuration
@EntityScan(basePackages = {"io.finflow.normalizer", "io.finflow.outbox"})
@EnableJpaRepositories(basePackages = {"io.finflow.normalizer", "io.finflow.outbox"})
public class JpaConfig {
}
