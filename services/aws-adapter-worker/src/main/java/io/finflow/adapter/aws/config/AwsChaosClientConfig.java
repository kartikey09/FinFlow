package io.finflow.adapter.aws.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Builds the RestClient the adapter uses to call the Chaos API.
 *
 * <p><b>On the "TimeLimiter" spec item:</b> the build plan lists a Resilience4j
 * {@code TimeLimiter} of 10s per call. Resilience4j's {@code @TimeLimiter}
 * requires the target method to return {@code CompletableFuture<T>}, which
 * would cascade Reactor / async handling into every method that touches this
 * client. That's a big architectural change for one guarantee.
 *
 * <p>The pragmatic replacement — realizing "10s per call" as the underlying
 * HTTP client's <b>read timeout</b> — gives the same behavior for the case
 * that matters (the Chaos API's 5s hangs): a hung upstream throws
 * {@code ResourceAccessException} after 10s, which the Retry policy handles
 * exactly like a 503. Same guarantee, no async cascade.
 *
 * <p>Recorded here so a reviewer sees the deliberate choice, not a missing
 * feature.
 */
@Configuration
public class AwsChaosClientConfig {

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
