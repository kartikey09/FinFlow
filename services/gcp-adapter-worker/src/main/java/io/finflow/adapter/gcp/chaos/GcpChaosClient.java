package io.finflow.adapter.gcp.chaos;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
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
 */
@Component
public class GcpChaosClient {

    private static final Logger log = LoggerFactory.getLogger(GcpChaosClient.class);
    private static final String INSTANCE = "gcp-chaos";

    private static final Executor CHAOS_EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "gcp-chaos-timelimiter");
        t.setDaemon(true);
        return t;
    });

    private final RestClient restClient;

    public GcpChaosClient(RestClient chaosApiRestClient) {
        this.restClient = chaosApiRestClient;
    }

    @Retry(name = INSTANCE)
    @CircuitBreaker(name = INSTANCE)
    @TimeLimiter(name = INSTANCE)
    @Bulkhead(name = INSTANCE)
    public CompletableFuture<Void> postCommitmentAction(String commitmentId, String action) {
        return CompletableFuture.supplyAsync(() -> {
            String path = "/gcp/billing/commitments/" + commitmentId + "/" + action;
            log.debug("Chaos call POST {}", path);
            restClient.post()
                    .uri(path)
                    .retrieve()
                    .toBodilessEntity();
            return null;
        }, CHAOS_EXECUTOR);
    }
}
