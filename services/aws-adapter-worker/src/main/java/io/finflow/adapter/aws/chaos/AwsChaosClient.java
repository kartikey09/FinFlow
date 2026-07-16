package io.finflow.adapter.aws.chaos;

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
 * Calls the Chaos API's AWS rebalance endpoints, wrapped in the full Resilience4j
 * stack (Retry + CircuitBreaker + TimeLimiter + Bulkhead, instance "aws-chaos").
 *
 * <h2>Day 24: trace context does NOT cross a thread boundary by itself</h2>
 *
 * <p>{@code @TimeLimiter} requires a {@code CompletableFuture} return type, which is
 * why the HTTP call lives inside {@code supplyAsync(..., CHAOS_EXECUTOR)}. That is
 * a genuine thread hop: the lambda body runs on an "aws-chaos-timelimiter" thread,
 * NOT the Kafka listener thread that called us.
 *
 * <p>Tracing context is held in a ThreadLocal. So on the executor thread there is
 * no current span — and the RestClient's observation, finding no parent, opens a
 * BRAND NEW ROOT TRACE. The chaos-api call then appears in Jaeger as an unrelated
 * orphan instead of a child of the saga. Everything still works; the trace is just
 * quietly wrong, which is the worst kind of wrong.
 *
 * <p>The fix is three lines: grab the current {@link Observation} on the CALLING
 * thread (where it still exists), then re-open its scope inside the lambda so the
 * executor thread's ThreadLocal is populated before RestClient runs. The
 * try/finally guarantees we close the scope even when chaos throws.
 *
 * <p>Note we deliberately use {@code micrometer-observation} (already on the
 * classpath via actuator) rather than pulling in the {@code context-propagation}
 * module. Re-opening the Observation's scope makes the OTel span current as a side
 * effect, because the tracing ObservationHandler ties the two together. Same
 * result, one fewer dependency.
 *
 * <p>The Timer stays INSIDE the lambda on purpose (Day 22): it measures ONE attempt,
 * so a retried call records three samples rather than one fat 3x-budget sample.
 */
@Component
public class AwsChaosClient {

    private static final Logger log = LoggerFactory.getLogger(AwsChaosClient.class);
    private static final String INSTANCE = "aws-chaos";
    private static final String LATENCY_METRIC = "finflow.chaos.call.latency";

    /**
     * Named executor for the CompletableFuture.supplyAsync. Daemon threads so
     * it never blocks JVM shutdown; effectively bounded by the Bulkhead's
     * 10-permit ceiling in practice.
     */
    private static final Executor CHAOS_EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "aws-chaos-timelimiter");
        t.setDaemon(true);
        return t;
    });

    private final RestClient restClient;
    private final MeterRegistry meterRegistry;
    private final ObservationRegistry observationRegistry;   // Day 24

    public AwsChaosClient(RestClient chaosApiRestClient,
                          MeterRegistry meterRegistry,
                          ObservationRegistry observationRegistry) {
        this.restClient = chaosApiRestClient;
        this.meterRegistry = meterRegistry;
        this.observationRegistry = observationRegistry;
    }

    /**
     * Call one of the /aws/commitments/... rebalance endpoints. The returned
     * future completes normally on 2xx; completes exceptionally (wrapped in
     * ExecutionException at the call site) on 4xx/5xx that survives Retry, or
     * on a TimeLimiter timeout.
     */
    @Retry(name = INSTANCE)
    @CircuitBreaker(name = INSTANCE)
    @TimeLimiter(name = INSTANCE)
    @Bulkhead(name = INSTANCE)
    public CompletableFuture<Void> postCommitmentAction(String commitmentId, String action) {

        // Day 24 — capture on the CALLER thread, where the Kafka receiver span is
        // still current. Must happen out here; inside the lambda it's already gone.
        Observation parent = observationRegistry.getCurrentObservation();

        return CompletableFuture.supplyAsync(() -> {
            // Day 24 — re-open the parent scope on the executor thread so the
            // RestClient span attaches to the saga's trace instead of starting
            // a new root. Null-safe: untraced callers (tests) just get no scope.
            Observation.Scope scope = (parent != null) ? parent.openScope() : null;
            try {
                String path = "/aws/commitments/" + commitmentId + "/" + action;
                log.debug("Chaos call POST {}", path);
                Timer.Sample sample = Timer.start(meterRegistry);
                String outcome = "success";
                try {
                    restClient.post()
                            .uri(path)
                            .retrieve()
                            .toBodilessEntity();   // discard body; 2xx is all we need
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
