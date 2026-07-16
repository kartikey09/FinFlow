package io.finflow.ingestion.gcp.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * GCP mirror of {@code AwsCurClient}.
 *
 * <p><b>Day 24:</b> re-opens the caller's Observation inside the {@code supplyAsync}
 * lambda so the HTTP span joins the poll's trace rather than starting a new root.
 * See {@code AwsChaosClient} for why the thread hop breaks tracing.
 */
@Component
public class GcpBillingClient{

    private static final Executor CHAOS_EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "gcp-billing-timelimiter");
        t.setDaemon(true);
        return t;
    });

    private final RestClient restClient;
    private final ObservationRegistry observationRegistry;   // Day 24

    public GcpBillingClient(RestClient chaosApiRestClient,
                            ObservationRegistry observationRegistry) {
        this.restClient = chaosApiRestClient;
        this.observationRegistry = observationRegistry;
    }

    @Retry(name = "chaos-api")
    @CircuitBreaker(name = "chaos-api")
    @TimeLimiter(name = "chaos-api")
    public CompletableFuture<GcpBillingExportPage> fetchPage(String nextPageToken) {

        // Day 24 — capture on the caller (scheduler) thread.
        Observation parent = observationRegistry.getCurrentObservation();

        return CompletableFuture.supplyAsync(() -> {
            Observation.Scope scope = (parent != null) ? parent.openScope() : null;
            try {
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
            } finally {
                if (scope != null) {
                    scope.close();
                }
            }
        }, CHAOS_EXECUTOR);
    }
}
