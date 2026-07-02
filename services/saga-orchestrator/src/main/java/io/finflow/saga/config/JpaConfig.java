package io.finflow.saga.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Brings the outbox-starter's persistence into this service's scan.
 *
 * <p><b>Fifth</b> byte-identical copy across the codebase (two ingestors +
 * cost-normalizer + commitment-tracker + this). Every additional producer
 * strengthens the case for folding this into the outbox-starter itself —
 * kept out only because the library contract is currently locked.
 */
@Configuration
@EntityScan(basePackages = {"io.finflow.saga", "io.finflow.outbox"})
@EnableJpaRepositories(basePackages = {"io.finflow.saga", "io.finflow.outbox"})
public class JpaConfig {
}
