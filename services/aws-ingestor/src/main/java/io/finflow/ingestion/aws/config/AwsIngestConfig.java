package io.finflow.ingestion.aws.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestClient;

/**
 * Wiring for the AWS ingest poll loop.
 *
 * <p>{@code @EnableScheduling} switches on the {@code @Scheduled} tick in
 * {@code AwsIngestScheduler}.
 *
 * <p>The {@code chaosApiRestClient} bean here is the one {@code AwsCurClient} uses
 * to pull CUR pages — distinct from the health-probe client in
 * {@link ChaosApiClientConfig}. Spring resolves them by parameter name.
 *
 * <p><b>Day 24:</b> injects Spring's auto-configured {@code RestClient.Builder}
 * instead of the static {@code RestClient.builder()}. This one IS on the trace
 * path: the poll span -> this HTTP call -> chaos-api's server span. With the static
 * builder there's no observation, so no client span and no {@code traceparent}
 * header, and chaos-api detaches into its own orphan trace.
 */
@Configuration
@EnableScheduling
public class AwsIngestConfig {

    @Bean
    public RestClient chaosApiRestClient(RestClient.Builder builder,       // Day 24
                                         @Value("${finflow.chaos-api.base-url}") String baseUrl) {
        return builder
                .baseUrl(baseUrl)
                .build();
    }
}
