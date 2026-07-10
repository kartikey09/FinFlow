package io.finflow.adapter.aws.chaos;

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
 * The single point where the adapter reaches out to the Chaos API.
 *
 * <p>Wraps every call in the full Resilience4j stack:
 * <ul>
 *   <li>{@code @Retry("aws-chaos")} — 3 attempts, 200ms base, exp backoff, 0.3 jitter</li>
 *   <li>{@code @CircuitBreaker("aws-chaos")} — opens at 50% failure over the last 20 calls</li>
 *   <li>{@code @TimeLimiter("aws-chaos")} — 7s per call (longer than the 5s chaos hang)</li>
 *   <li>{@code @Bulkhead("aws-chaos")} — max 10 concurrent calls (semaphore-based)</li>
 * </ul>
 *
 * <h2>Day 21 change: TimeLimiter added, at last</h2>
 *
 * <p>Days 18/19 documented "TimeLimiter realized as HTTP read-timeout" because
 * Resilience4j's {@code @TimeLimiter} requires the method to return
 * {@code CompletableFuture<T>}. Day 21 explicitly calls for TimeLimiter on all
 * HTTP calls, so we take the plunge — but keep the async pattern <b>local</b>.
 * This method now returns {@code CompletableFuture<Void>}; the only caller
 * ({@code CommandExecutor}) blocks on it with {@code .get()}. No Reactor is
 * introduced anywhere else.
 *
 * <p>The RestClient read-timeout (see {@code AwsChaosClientConfig}) STAYS as a
 * safety net below the 7s TimeLimiter. Cancelling an in-flight HTTP call
 * doesn't always release the socket immediately, so the read-timeout catches a
 * truly-hung connection that leaks past TimeLimiter's cancellation.
 *
 * <h2>Annotation order matters</h2>
 * Aspects apply outside-in in declaration order:
 * <pre>Retry → CircuitBreaker → TimeLimiter → Bulkhead → method body</pre>
 * so one Kafka message = one CB unit (retries counted as one), a Bulkhead
 * permit is held only for one attempt, and a 7s TimeLimiter fires per attempt
 * (not across the whole 3× budget).
 */
@Component
public class AwsChaosClient {

    private static final Logger log = LoggerFactory.getLogger(AwsChaosClient.class);
    private static final String INSTANCE = "aws-chaos";

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

    public AwsChaosClient(RestClient chaosApiRestClient) {
        this.restClient = chaosApiRestClient;
    }

    /**
     * Call one of the /aws/commitments/... rebalance endpoints. The returned
     * future completes normally on 2xx; completes exceptionally (wrapped in
     * ExecutionException at the call site) on 4xx/5xx that survives Retry, or
     * on a TimeLimiter timeout.
     *
     * <p>Caller pattern in CommandExecutor:
     * <pre>{@code
     *   try {
     *     chaosClient.postCommitmentAction(id, action).get();  // blocks
     *     recordAndEmitSuccess(...);
     *   } catch (ExecutionException e) {
     *     recordAndEmitFailure(..., describe(e.getCause()));
     *   }
     * }</pre>
     */
    @Retry(name = INSTANCE)
    @CircuitBreaker(name = INSTANCE)
    @TimeLimiter(name = INSTANCE)
    @Bulkhead(name = INSTANCE)
    public CompletableFuture<Void> postCommitmentAction(String commitmentId, String action) {
        return CompletableFuture.supplyAsync(() -> {
            String path = "/aws/commitments/" + commitmentId + "/" + action;
            log.debug("Chaos call POST {}", path);
            restClient.post()
                    .uri(path)
                    .retrieve()
                    .toBodilessEntity();   // discard body; 2xx is all we need
            return null;
        }, CHAOS_EXECUTOR);
    }
}
