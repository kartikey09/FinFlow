package io.finflow.ingestion.aws.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestClient;

/**
 * What this module does:
 * This class is the configuration backbone for the `aws-ingestor` service.
 * It sets up the background task scheduler required for polling and constructs
 * the HTTP client used to talk to the Chaos API.
 *
 * Why we built it (The Architectural Decoupling):
 * 1. Explicit Scheduling: We already turned on `@EnableScheduling` inside the
 * `outbox-starter` for the database cleanup job. Why do it again here?
 * Because of decoupling. If a developer turns off the outbox retention job
 * (`finflow.outbox.retention.enabled=false`), the outbox scheduler shuts down.
 * If we relied on that, our AWS polling loop would silently die too. By explicitly
 * enabling it here, we guarantee the ingestor always runs, regardless of what
 * the outbox is doing.
 * 2. Centralized HTTP Config: By pre-configuring the `RestClient` with the base
 * URL here, the `AwsCurClient` never has to worry about hostnames or ports.
 * It only deals with routing paths (like `/aws/cost-and-usage-report`).
 */

@Configuration
// @EnableScheduling tells Spring Boot to activate its background task executor.
// Without this, any method annotated with `@Scheduled` (like your polling loop)
// will be completely ignored and will never fire.
@EnableScheduling
public class AwsIngestConfig {
    @Bean
    // @Value looks into your `application.yml` file, finds the property named
    // `finflow.chaos-api.base-url` (e.g., "http://localhost:9000"), and injects
    // it into the `baseUrl` String variable.
    public RestClient chaosApiRestClient(@Value("${finflow.chaos-api.base-url}") String baseUrl) {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }
}

/// Building the Client
// Uses the Builder pattern to create a new RestClient.
// By setting the baseUrl here, every time this specific RestClient makes
// a GET request to "/some-path", it automatically prepends the base URL,
// resulting in "http://localhost:9000/some-path".
// Finally, it returns the fully assembled client to the Spring Application Context.