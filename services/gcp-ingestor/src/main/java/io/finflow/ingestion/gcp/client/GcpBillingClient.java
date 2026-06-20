package io.finflow.ingestion.gcp.client;

import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;


@Component
public class GcpBillingClient{
    private final RestClient restClient;

    public GcpBillingClient(RestClient chaosApiRestClient) {
        this.restClient = chaosApiRestClient;
    }

    @Retry(name = "chaos-api")
    public GcpBillingExportPage fetchPage(String nextPageToken) {
        return restClient.get()
                .uri(uriBuilder -> {
                    uriBuilder.path("/gcp/billing-export");
                    if (nextPageToken != null && !nextPageToken.isBlank()) {
                        uriBuilder.queryParam("nextPageToken", nextPageToken);
                    }
                    return uriBuilder.build();
                })
                .retrieve()
                .body(GcpBillingExportPage.class);
    }
}