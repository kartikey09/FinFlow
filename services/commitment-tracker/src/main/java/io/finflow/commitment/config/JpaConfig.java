package io.finflow.commitment.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Brings the outbox-starter's persistence into this service's scan, alongside
 * the tracker's own entities/repositories.
 *
 * <p>FOURTH copy of this exact class — same boilerplate as the two ingestors
 * and cost-normalizer. The rule of three says it should already be in the
 * library; the fourth occurrence makes the argument unanswerable. Kept here
 * only because the outbox-starter contract is currently locked.
 */
@Configuration
@EntityScan(basePackages = {"io.finflow.commitment", "io.finflow.outbox"})
@EnableJpaRepositories(basePackages = {"io.finflow.commitment", "io.finflow.outbox"})
public class JpaConfig {
}
