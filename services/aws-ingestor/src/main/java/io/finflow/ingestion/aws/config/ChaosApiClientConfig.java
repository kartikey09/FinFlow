package io.finflow.ingestion.aws.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * The RestClient used by {@code ChaosApiHealthIndicator} to probe
 * {@code /actuator/health} on chaos-api. Short timeouts (2s connect / 3s read)
 * because a health probe that hangs is worse than one that fails.
 *
 * <p>NOTE — this is a SECOND, separate RestClient bean in this service. The poll
 * loop's client ({@code chaosApiRestClient}, in {@link AwsIngestConfig}) is a
 * different bean with different timeouts. They're wired by parameter name, so
 * don't rename either one casually.
 *
 * <p><b>Day 24:</b> injects Spring's {@code RestClient.Builder} bean rather than the
 * static factory, so health probes are traced too. (Low stakes here — a health
 * probe isn't part of the business trace — but keeping every RestClient
 * consistently instrumented means there's no "which one is traced?" ambiguity
 * later.)
 */
@Configuration
public class ChaosApiClientConfig {

    @Bean
    public RestClient chaosApiClient(RestClient.Builder builder,           // Day 24
                                     @Value("${finflow.chaos-api.base-url}") String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(3));
        return builder
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }
}
