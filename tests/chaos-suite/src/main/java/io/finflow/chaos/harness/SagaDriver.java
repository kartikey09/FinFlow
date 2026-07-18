package io.finflow.chaos.harness;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Fires N rebalance sagas CONCURRENTLY at POST /sagas/rebalance.
 *
 * <p>Concurrency is the point, not a detail. Twenty sagas fired sequentially would
 * mostly not interact. Twenty fired at once contend for the same commitments, the same
 * DB rows, the same Kafka partitions, and the same Bulkhead permits (10) on the chaos
 * client — which is where optimistic-locking races and partial-failure interleavings
 * actually show up.
 *
 * <p>Every correlationId is prefixed with the run id, so a run can identify exactly
 * its own sagas in a database that also contains every previous run's.
 */
public final class SagaDriver {

    private static final Logger log = LoggerFactory.getLogger(SagaDriver.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final String sagaBaseUrl;

    public SagaDriver(String sagaBaseUrl) {
        this.sagaBaseUrl = sagaBaseUrl;
    }

    /**
     * @param runId   unique per run; becomes the correlationId prefix
     * @param n       how many sagas (the plan says 20)
     * @param vendor  "AWS" or "GCP"
     * @return the saga ids the orchestrator accepted
     */
    public List<UUID> fireConcurrently(String runId, int n, String vendor) {
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(n, 20));
        try {
            List<CompletableFuture<UUID>> futures = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                final String correlationId = runId + "-" + i;
                futures.add(CompletableFuture.supplyAsync(() -> start(correlationId, vendor), pool));
            }
            List<UUID> ids = new ArrayList<>();
            for (CompletableFuture<UUID> f : futures) {
                UUID id = f.join();
                if (id != null) ids.add(id);
            }
            log.info("fired {} saga(s) for run {} ({} accepted)", n, runId, ids.size());
            return ids;
        } finally {
            pool.shutdown();
            try {
                pool.awaitTermination(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private UUID start(String correlationId, String vendor) {
        String body = """
                {"correlationId":"%s","vendor":"%s"}
                """.formatted(correlationId, vendor);
        try {
            var res = Http.post(sagaBaseUrl + "/sagas/rebalance", body);
            if (res.statusCode() != 202 && res.statusCode() != 200) {
                log.warn("saga {} rejected: HTTP {} {}", correlationId, res.statusCode(), res.body());
                return null;
            }
            JsonNode node = JSON.readTree(res.body());
            return UUID.fromString(node.get("id").asText());
        } catch (Exception e) {
            // A saga we could not even START is not a saga we can hold the system to.
            // It is still worth shouting about: it usually means the orchestrator is down.
            log.warn("failed to start saga {}: {}", correlationId, e.toString());
            return null;
        }
    }
}
