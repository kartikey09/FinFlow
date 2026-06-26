package io.finflow.ingestion.gcp.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EntityScan(basePackages = {"io.finflow.ingestion.gcp", "io.finflow.outbox"})
@EnableJpaRepositories(basePackages = {"io.finflow.ingestion.gcp", "io.finflow.outbox"})
public class JpaConfig{
}