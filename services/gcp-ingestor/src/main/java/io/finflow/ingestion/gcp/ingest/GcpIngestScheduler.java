package io.finflow.ingestion.gcp.ingest;

import io.finflow.ingestion.gcp.client.GcpBillingClient;
import io.finflow.ingestion.gcp.client.GcpBillingExportPage;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Drives GCP ingestion on a fixed cadence (default every 30s, after a 12s warm-up
 * — slightly offset from aws-ingestor's 10s so the two don't poll in lockstep).
 *
 * <p>Network call OUTSIDE the transaction (retried by the client), then hand the
 * page to {@link GcpIngestService#persistPage}. {@code fixedDelay} prevents
 * overlap; a failure that survived retries is logged and retried next tick with
 * the cursor unmoved.
 *
 * <p>Day 21: fetchPage returns a CompletableFuture (TimeLimiter enforces a 7s
 * deadline). We block on it with .get(); the existing catch absorbs failures
 * and we retry next cycle. Nothing else about the poll loop changes.
 */
@Component
public class GcpIngestScheduler {

    private static final Logger log = LoggerFactory.getLogger(GcpIngestScheduler.class);

    private final GcpBillingClient client;
    private final GcpIngestService ingestService;
    private final ObservationRegistry observationRegistry;   // Day 24

    public GcpIngestScheduler(GcpBillingClient client,
                              GcpIngestService ingestService,
                              ObservationRegistry observationRegistry) {
        this.client = client;
        this.ingestService = ingestService;
        this.observationRegistry = observationRegistry;
    }

    @Scheduled(
            fixedDelayString = "${finflow.gcp.ingest.poll-interval:PT30S}",
            initialDelayString = "${finflow.gcp.ingest.initial-delay:PT12S}")
    public void poll() {
        // Day 24 — root span for the GCP ingest pipeline. Spring does NOT
        // instrument @Scheduled methods, so without this the outbox row would be
        // appended with no active span (trace_parent = NULL) and every downstream
        // consumer would start its own disconnected trace. See AwsIngestScheduler.
        Observation.createNotStarted("finflow.ingest.poll", observationRegistry)
                .lowCardinalityKeyValue("vendor", "gcp")
                .observe(this::doPoll);
    }

    /** The original poll body, unchanged. Runs inside the poll span. */
    private void doPoll() {
        try {
            String token = ingestService.currentToken();
            // Day 21: fetchPage returns CompletableFuture; block for the result.
            GcpBillingExportPage page = client.fetchPage(token).get();
            if (page == null || page.rows() == null) {
                log.warn("GCP ingest: empty billing-export response (token={}), skipping", token);
                return;
            }
            int persisted = ingestService.persistPage(page);
            log.info("GCP ingest: {} new of {} row(s), nextPageToken={}",
                    persisted, page.rows().size(), page.nextPageToken());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("GCP ingest poll interrupted");
        } catch (Exception e) {
            log.warn("GCP ingest poll failed (retry next cycle): {}", e.toString());
        }
    }
}
