package io.finflow.ingestion.gcp.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestClient;

/**
 * GCP mirror of {@code AwsIngestConfig}.
 *
 * <p><b>Day 24:</b> injects Spring's auto-configured {@code RestClient.Builder} so
 * the poll loop's HTTP call to chaos-api produces a client span and carries the
 * {@code traceparent} header. See AwsChaosClientConfig for the full explanation of
 * why the static {@code RestClient.builder()} silently breaks tracing.
 */
@Configuration
@EnableScheduling
public class GcpIngestConfig{

    @Bean
    public RestClient chaosApiRestClient(RestClient.Builder builder,       // Day 24
                                         @Value("${finflow.chaos-api.base-url}") String baseUrl){
        return builder
                .baseUrl(baseUrl)
                .build();
    }
}
