package io.finflow.ingestion.aws.client;

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
 * Pulls Cost-and-Usage-Report pages from the Chaos API (which stands in for AWS).
 *
 * <p><b>Day 24:</b> {@code @TimeLimiter} forces a {@code CompletableFuture} return,
 * so the HTTP call runs on the "aws-cur-timelimiter" pool — a different thread from
 * the {@code @Scheduled} poll that called it. Tracing context lives in a
 * ThreadLocal and does not follow. Without re-opening the parent Observation inside
 * the lambda, the RestClient call starts its own root trace and the ingest trace
 * splits in two.
 *
 * <p>Capture on the caller thread, re-open on the executor thread. See
 * {@code AwsChaosClient} for the full write-up.
 */
@Component
public class AwsCurClient {

    private static final Executor CHAOS_EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "aws-cur-timelimiter");
        t.setDaemon(true);
        return t;
    });

    private final RestClient restClient;
    private final ObservationRegistry observationRegistry;   // Day 24

    public AwsCurClient(RestClient chaosApiRestClient,
                        ObservationRegistry observationRegistry) {
        this.restClient = chaosApiRestClient;
        this.observationRegistry = observationRegistry;
    }

    @Retry(name = "chaos-api")
    @CircuitBreaker(name = "chaos-api")
    @TimeLimiter(name = "chaos-api")
    public CompletableFuture<CostAndUsageReportPage> fetchPage(String nextToken){

        // Day 24 — capture the poll span while we're still on the scheduler thread.
        Observation parent = observationRegistry.getCurrentObservation();

        return CompletableFuture.supplyAsync(() -> {
            Observation.Scope scope = (parent != null) ? parent.openScope() : null;
            try {
                return restClient.get()
                        .uri(uriBuilder -> {
                            uriBuilder.path("/aws/cost-and-usage-report");
                            if (nextToken != null && !nextToken.isBlank()){
                                uriBuilder.queryParam("nextToken", nextToken);
                            }
                            return uriBuilder.build();
                        })
                        .retrieve()
                        .body(CostAndUsageReportPage.class);
            } finally {
                if (scope != null) {
                    scope.close();
                }
            }
        }, CHAOS_EXECUTOR);
    }
}
