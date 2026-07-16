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
 * <p><b>Day 21 note:</b> the RestClient read-timeout stays as a backstop BELOW
 * the Resilience4j {@code @TimeLimiter} (7s). Cancelling an in-flight future
 * doesn't always release the socket, so a hard read-timeout catches a connection
 * that leaks past TimeLimiter's cancellation.
 *
 * <h2>Day 24: THE SILENT TRACE-BREAKER — read this before you "simplify" it</h2>
 *
 * <p>This bean used to call the STATIC factory {@code RestClient.builder()}. That
 * compiles, runs, and looks completely fine — and it silently destroys distributed
 * tracing.
 *
 * <p>Spring Boot auto-configures a {@code RestClient.Builder} BEAN and applies
 * {@code ObservationRestClientCustomizer} to it. That customizer is what installs
 * the client-side observation — the thing that creates the HTTP client span AND
 * injects the {@code traceparent} header into the outgoing request. The static
 * {@code RestClient.builder()} factory method gets NONE of that: it hands you a
 * bare builder with no customizers applied.
 *
 * <p>The symptom is nasty because nothing errors. Calls still succeed. But
 * chaos-api's server span has no parent, so it shows up in Jaeger as a separate
 * orphan trace instead of a child of the adapter's span. You end up staring at two
 * unrelated traces wondering why the tree is broken.
 *
 * <p>The fix is to INJECT the builder ({@code RestClient.Builder builder}) instead
 * of calling the static factory. Spring hands you the instrumented one. Everything
 * else here — baseUrl, timeouts, request factory — is unchanged.
 *
 * <p>{@code RestClient.Builder} is a PROTOTYPE-scoped bean, so each injection point
 * gets its own fresh instance. Two configs can safely inject it and apply different
 * baseUrls without stepping on each other.
 */
@Configuration
public class AwsChaosClientConfig {

    @Bean
    public RestClient chaosApiRestClient(
            RestClient.Builder builder,                                    // Day 24: injected, NOT RestClient.builder()
            @Value("${finflow.chaos-api.base-url}") String baseUrl,
            @Value("${finflow.chaos-api.read-timeout:10s}") Duration readTimeout,
            @Value("${finflow.chaos-api.connect-timeout:5s}") Duration connectTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setReadTimeout(readTimeout);
        factory.setConnectTimeout(connectTimeout);
        return builder
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }
}
