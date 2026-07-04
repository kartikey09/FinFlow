package io.finflow.adapter.gcp.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * RestClient bean for the GCP-side Chaos API calls.
 *
 * <p>Same design as {@code AwsChaosClientConfig}: the plan's "TimeLimiter: 10s
 * per call" is realized as an HTTP <b>read timeout</b> rather than a
 * Resilience4j {@code @TimeLimiter}, because that annotation would force
 * {@code CompletableFuture} returns and cascade Reactor into every touching
 * method. The 10s read-timeout catches the chaos-api's 5s hangs and turns
 * them into {@code ResourceAccessException} the Retry policy handles.
 */
@Configuration
public class GcpChaosClientConfig {

    @Bean
    public RestClient chaosApiRestClient(
            @Value("${finflow.chaos-api.base-url}") String baseUrl,
            @Value("${finflow.chaos-api.read-timeout:10s}") Duration readTimeout,
            @Value("${finflow.chaos-api.connect-timeout:5s}") Duration connectTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setReadTimeout(readTimeout);
        factory.setConnectTimeout(connectTimeout);
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }
}
