package io.finflow.adapter.aws.chaos;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * The single point where the adapter reaches out to the Chaos API.
 *
 * <p>Wraps every call in the Resilience4j stack the plan specifies:
 * <ul>
 *   <li>{@code @Retry("aws-chaos")} — 3 attempts, exponential backoff with jitter
 *       (configured in application.yml). Absorbs the ~20% injected 503s and any
 *       transient network hiccup.</li>
 *   <li>{@code @CircuitBreaker("aws-chaos")} — opens after 5 failures in a 60s
 *       window; stops hammering an obviously-broken upstream. Half-open probes
 *       let it recover automatically.</li>
 *   <li>{@code @Bulkhead("aws-chaos")} — max 10 concurrent calls, so a slow
 *       upstream can't starve every worker thread. Semaphore-based (cheap).</li>
 * </ul>
 *
 * <p>The "TimeLimiter: 10s per call" spec item is realized as the underlying
 * RestClient's read timeout — see {@code AwsChaosClientConfig} for the
 * rationale (avoiding the CompletableFuture cascade that
 * {@code @TimeLimiter} would force).
 *
 * <p><b>Method ordering of the annotations is not arbitrary.</b> Aspects are
 * applied outside-in in the order they're declared. So a call proceeds:
 * Retry &rarr; CircuitBreaker &rarr; Bulkhead &rarr; actual call. The retry
 * counts as ONE unit of circuit-breaker load, and the bulkhead permit is only
 * held for the duration of one attempt. That's the shape you want; the
 * alternative (bulkhead outside retry) would hold a permit across all 3
 * attempts and could starve throughput during a Chaos storm.
 */
@Component
public class AwsChaosClient {

    private static final Logger log = LoggerFactory.getLogger(AwsChaosClient.class);
    private static final String INSTANCE = "aws-chaos";

    private final RestClient restClient;

    public AwsChaosClient(RestClient chaosApiRestClient) {
        this.restClient = chaosApiRestClient;
    }

    /**
     * Call one of the /aws/commitments/... rebalance endpoints. Returns silently
     * on 2xx; throws an unchecked HTTP client exception on 4xx/5xx that
     * survives the retry policy, which the caller translates into a failure event.
     */
    @Retry(name = INSTANCE)
    @CircuitBreaker(name = INSTANCE)
    @Bulkhead(name = INSTANCE)
    public void postCommitmentAction(String commitmentId, String action) {
        String path = "/aws/commitments/" + commitmentId + "/" + action;
        log.debug("Chaos call POST {}", path);
        restClient.post()
                .uri(path)
                .retrieve()
                .toBodilessEntity();   // discard response body; 2xx is all we need
    }
}
