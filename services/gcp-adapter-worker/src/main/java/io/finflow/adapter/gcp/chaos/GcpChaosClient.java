package io.finflow.adapter.gcp.chaos;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * GCP mirror of {@code AwsChaosClient} — same Resilience4j stack, different
 * instance name (so the two adapters have independent circuit breakers and
 * bulkhead pools).
 *
 * <p>Path prefix is {@code /gcp/billing/commitments/...} per the plan.
 *
 * <p><b>Annotation ORDER intentional</b> (mirrors AWS's rationale):
 * outermost {@code @Retry} → {@code @CircuitBreaker} → innermost
 * {@code @Bulkhead}. A retry counts as one CB attempt; a Bulkhead permit is
 * held for one attempt, not for all three. Reversed, a Chaos storm could
 * exhaust the semaphore for ~2s per message.
 */
@Component
public class GcpChaosClient {

    private static final Logger log = LoggerFactory.getLogger(GcpChaosClient.class);
    private static final String INSTANCE = "gcp-chaos";

    private final RestClient restClient;

    public GcpChaosClient(RestClient chaosApiRestClient) {
        this.restClient = chaosApiRestClient;
    }

    /**
     * Call one of the /gcp/billing/commitments/... rebalance endpoints. Returns
     * silently on 2xx; throws on 4xx/5xx that survives Retry, which the caller
     * translates into a failure event.
     */
    @Retry(name = INSTANCE)
    @CircuitBreaker(name = INSTANCE)
    @Bulkhead(name = INSTANCE)
    public void postCommitmentAction(String commitmentId, String action) {
        String path = "/gcp/billing/commitments/" + commitmentId + "/" + action;
        log.debug("Chaos call POST {}", path);
        restClient.post()
                .uri(path)
                .retrieve()
                .toBodilessEntity();
    }
}
