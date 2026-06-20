package io.finflow.ingestion.gcp.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestClient;

@Configuration
@EnableScheduling
public class GcpIngestConfig{

    @Bean
    public RestClient chaosApiRestClient(@Value("${finflow.chaos-api.base-url}") String baseUrl){
        return RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }
}