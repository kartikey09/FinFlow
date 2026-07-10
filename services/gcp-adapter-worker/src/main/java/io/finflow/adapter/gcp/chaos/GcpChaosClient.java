package io.finflow.adapter.gcp.chaos;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * GCP mirror of {@link io.finflow.adapter.aws.chaos.AwsChaosClient AwsChaosClient}.
 * Same Day-21 TimeLimiter cascade, same rationale, its own {@code gcp-chaos}
 * instance so the two adapters keep independent circuit breakers and bulkhead
 * pools.
 *
 * <p>Path prefix is {@code /gcp/billing/commitments/...} per the plan.
 *
 * <p><b>Annotation order</b> (outermost first):
 * {@code Retry → CircuitBreaker → TimeLimiter → Bulkhead → body}.
 *
 * <h2>Day 22: finflow.chaos.call.latency</h2>
 * Same instrumentation as the AWS client — each attempt timed, tagged by
 * {@code endpoint} and {@code outcome}. The metric NAME is shared across both
 * adapters; the {@code endpoint} tag distinguishes them implicitly (aws uses
 * lock/verify/..., gcp uses the same action names but the saga vendor tag on
 * the saga metrics ties them back). If per-vendor latency split is ever needed,
 * add a {@code vendor} tag here.
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

    public GcpChaosClient(RestClient chaosApiRestClient, MeterRegistry meterRegistry) {
        this.restClient = chaosApiRestClient;
        this.meterRegistry = meterRegistry;
    }

    @Retry(name = INSTANCE)
    @CircuitBreaker(name = INSTANCE)
    @TimeLimiter(name = INSTANCE)
    @Bulkhead(name = INSTANCE)
    public CompletableFuture<Void> postCommitmentAction(String commitmentId, String action) {
        return CompletableFuture.supplyAsync(() -> {
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
        }, CHAOS_EXECUTOR);
    }
}
