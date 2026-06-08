package io.finflow.ingestion.aws.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * A RestClient pointed at the chaos-api. Day 5 uses it only for a health ping
 * (see ChaosApiHealthIndicator); Day 11's poll loop reuses this same client to
 * pull CUR pages. Short timeouts so a chaos-injected hang doesn't wedge us.
 */
@Configuration
public class ChaosApiClientConfig {

    @Bean
    public RestClient chaosApiClient(@Value("${finflow.chaos-api.base-url}") String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(3));
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }
}
