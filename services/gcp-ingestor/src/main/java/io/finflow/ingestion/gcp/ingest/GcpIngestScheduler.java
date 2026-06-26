package io.finflow.ingestion.gcp.ingest;

import io.finflow.ingestion.gcp.client.GcpBillingClient;
import io.finflow.ingestion.gcp.client.GcpBillingExportPage;
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
 */
@Component
public class GcpIngestScheduler {

    private static final Logger log = LoggerFactory.getLogger(GcpIngestScheduler.class);

    private final GcpBillingClient client;
    private final GcpIngestService ingestService;

    public GcpIngestScheduler(GcpBillingClient client, GcpIngestService ingestService) {
        this.client = client;
        this.ingestService = ingestService;
    }

    @Scheduled(
            fixedDelayString = "${finflow.gcp.ingest.poll-interval:30s}",
            initialDelayString = "${finflow.gcp.ingest.initial-delay:12s}")
    public void poll() {
        try {
            String token = ingestService.currentToken();
            GcpBillingExportPage page = client.fetchPage(token);
            if (page == null || page.rows() == null) {
                log.warn("GCP ingest: empty billing-export response (token={}), skipping", token);
                return;
            }
            int persisted = ingestService.persistPage(page);
            log.info("GCP ingest: {} new of {} row(s), nextPageToken={}",
                    persisted, page.rows().size(), page.nextPageToken());
        } catch (Exception e) {
            log.warn("GCP ingest poll failed (retry next cycle): {}", e.toString());
        }
    }
}
