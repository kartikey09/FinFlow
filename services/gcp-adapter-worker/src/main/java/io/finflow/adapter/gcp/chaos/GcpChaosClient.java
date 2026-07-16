package io.finflow.adapter.gcp.chaos;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * GCP mirror of {@code AwsChaosClient} (Resilience4j instance "gcp-chaos").
 *
 * <p><b>Day 24:</b> carries the trace context across the {@code supplyAsync} thread
 * hop. Without it the RestClient call runs on a "gcp-chaos-timelimiter" thread with
 * an empty tracing ThreadLocal, so chaos-api's span becomes an orphan root instead
 * of a child of the saga. See the long explanation on AwsChaosClient.
 */
@Component
public class GcpChaosClient {

    private static final Logger log = LoggerFactory.getLogger(GcpChaosClient.class);
    private static final String INSTANCE = "gcp-chaos";
    private static final String LATENCY_METRIC = "finflow.chaos.call.latency";

    private static final Executor CHAOS_EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "gcp-chaos-timelimiter");
        t.setDaemon(true);
        return t;
    });

    private final RestClient restClient;
    private final MeterRegistry meterRegistry;
    private final ObservationRegistry observationRegistry;   // Day 24

    public GcpChaosClient(RestClient chaosApiRestClient,
                          MeterRegistry meterRegistry,
                          ObservationRegistry observationRegistry) {
        this.restClient = chaosApiRestClient;
        this.meterRegistry = meterRegistry;
        this.observationRegistry = observationRegistry;
    }

    @Retry(name = INSTANCE)
    @CircuitBreaker(name = INSTANCE)
    @TimeLimiter(name = INSTANCE)
    @Bulkhead(name = INSTANCE)
    public CompletableFuture<Void> postCommitmentAction(String commitmentId, String action) {

        // Day 24 — capture on the caller thread (see AwsChaosClient for why).
        Observation parent = observationRegistry.getCurrentObservation();

        return CompletableFuture.supplyAsync(() -> {
            Observation.Scope scope = (parent != null) ? parent.openScope() : null;
            try {
                String path = "/gcp/billing/commitments/" + commitmentId + "/" + action;
                log.debug("Chaos call POST {}", path);
                Timer.Sample sample = Timer.start(meterRegistry);
                String outcome = "success";
                try {
                    restClient.post()
                            .uri(path)
                            .retrieve()
                            .toBodilessEntity();
                    return null;
                } catch (RuntimeException e) {
                    outcome = "failure";
                    throw e;
                } finally {
                    sample.stop(Timer.builder(LATENCY_METRIC)
                            .description("Latency of one Chaos API call attempt, by endpoint and outcome")
                            .tag("endpoint", action)
                            .tag("outcome", outcome)
                            .publishPercentileHistogram()
                            .register(meterRegistry));
                }
            } finally {
                if (scope != null) {
                    scope.close();
                }
            }
        }, CHAOS_EXECUTOR);
    }
}
