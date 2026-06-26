package io.finflow.ingestion.gcp.client;

import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Fetches one page of GCP billing-export data from the Chaos API.
 *
 * <p>Same resilience as the AWS client: {@code @Retry(name="chaos-api")} absorbs
 * the injected 503s (3 attempts, 1s apart, 5xx/connection only). The query
 * parameter is {@code nextPageToken} (GCP's name), not {@code nextToken}.
 */

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