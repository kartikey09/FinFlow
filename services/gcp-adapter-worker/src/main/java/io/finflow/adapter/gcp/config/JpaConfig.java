package io.finflow.adapter.gcp.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * SEVENTH byte-identical copy across the codebase. Belongs in the outbox-starter.
 * The reason it hasn't moved yet — the library contract was locked during Week 2
 * — is now genuinely running out of runway, since Day 20 will edit outbox-starter
 * behavior anyway (DLT wiring). Refactor scheduled for the Day 20 morning.
 */
@Configuration
@EntityScan(basePackages = {"io.finflow.adapter.gcp", "io.finflow.outbox"})
@EnableJpaRepositories(basePackages = {"io.finflow.adapter.gcp", "io.finflow.outbox"})
public class JpaConfig {
}
