package io.finflow.adapter.gcp.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * GCP mirror of {@code AwsChaosClientConfig}.
 *
 * <p><b>Day 24:</b> injects Spring's auto-configured {@code RestClient.Builder}
 * instead of the static {@code RestClient.builder()} factory. Only the injected
 * builder carries {@code ObservationRestClientCustomizer}, which creates the HTTP
 * client span and injects the {@code traceparent} header. Without this, chaos-api
 * appears in Jaeger as an orphan trace instead of a child of the adapter's span.
 * See the long note in AwsChaosClientConfig.
 */
@Configuration
public class GcpChaosClientConfig {

    @Bean
    public RestClient chaosApiRestClient(
            RestClient.Builder builder,                                    // Day 24
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
