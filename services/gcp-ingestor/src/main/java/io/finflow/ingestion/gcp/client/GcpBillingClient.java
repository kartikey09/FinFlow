package io.finflow.ingestion.gcp.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Fetches one page of GCP billing-export data from the Chaos API.
 *
 * <p>Day 21: full HTTP resilience stack, mirroring aws-ingestor's AwsCurClient.
 * {@code @Retry} + {@code @CircuitBreaker} + {@code @TimeLimiter}, all under the
 * {@code "chaos-api"} instance. The TimeLimiter forces a CompletableFuture
 * return; the scheduler's poll() blocks on it with .get(). The query parameter
 * is {@code nextPageToken} (GCP's name), not {@code nextToken}.
 */
@Component
public class GcpBillingClient{

    private static final Executor CHAOS_EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "gcp-billing-timelimiter");
        t.setDaemon(true);
        return t;
    });

    private final RestClient restClient;

    public GcpBillingClient(RestClient chaosApiRestClient) {
        this.restClient = chaosApiRestClient;
    }

    @Retry(name = "chaos-api")
    @CircuitBreaker(name = "chaos-api")
    @TimeLimiter(name = "chaos-api")
    public CompletableFuture<GcpBillingExportPage> fetchPage(String nextPageToken) {
        return CompletableFuture.supplyAsync(() ->
                restClient.get()
                        .uri(uriBuilder -> {
                            uriBuilder.path("/gcp/billing-export");
                            if (nextPageToken != null && !nextPageToken.isBlank()) {
                                uriBuilder.queryParam("nextPageToken", nextPageToken);
                            }
                            return uriBuilder.build();
                        })
                        .retrieve()
                        .body(GcpBillingExportPage.class),
                CHAOS_EXECUTOR);
    }
}
