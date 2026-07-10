package io.finflow.ingestion.aws.ingest;

import io.finflow.ingestion.aws.client.AwsCurClient;
import io.finflow.ingestion.aws.client.CostAndUsageReportPage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * What this module does:
 * This class is the engine that drives the entire AWS data ingestion process.
 * It wakes up on a timer, asks the database where it left off (the cursor),
 * reaches out to AWS (via the Chaos API) to get the next page of data, and
 * hands that data off to be saved.
 *
 * Why we built it (The "Network-in-Transaction" Anti-Pattern):
 * You might wonder why we don't just put all this logic inside the
 * `AwsIngestService`. This is a crucial senior-level design decision.
 * Database transactions should be as short as physically possible.
 * If you open a `@Transactional` block, and THEN make an HTTP call to AWS
 * that takes 5 seconds, you are holding a database connection hostage for
 * 5 seconds. Under heavy load, you will exhaust your connection pool and
 * crash your database.
 * * By using this Scheduler class, we separate the steps:
 * 1. Fast read (get cursor).
 * 2. SLOW network call (fetch page) -> NO TRANSACTION HELD.
 * 3. Fast write (persist page) -> OPENS AND CLOSES TRANSACTION INSTANTLY.
 *
 * <p>Day 21: fetchPage now returns a CompletableFuture (so the TimeLimiter can
 * enforce a 7s deadline). We block on it with .get(); the existing catch still
 * absorbs failures and we retry on the next scheduled cycle. Nothing else about
 * the poll loop changes.
 */
@Component
public class AwsIngestScheduler {
    // to print standard operational metrics (like "Saved 100 rows") and to safely log
    // errors when the network inevitably blips.
    private static final Logger log = LoggerFactory.getLogger(AwsIngestScheduler.class);

    // dependencies - The HTTP client (to talk to AWS) and the Service (to talk to the Database)
    private final AwsCurClient client;
    private final AwsIngestService ingestService;

    public AwsIngestScheduler(AwsCurClient client, AwsIngestService ingestService){
        this.client = client;
        this.ingestService = ingestService;
    }

    @Scheduled(
            fixedDelayString = "${finflow.aws.ingest.poll-interval:PT30S}",
            initialDelayString = "${finflow.aws.ingest.initial-delay:PT10S}")
    public void poll(){
        try{
            String token = ingestService.currentToken();
            // Day 21: fetchPage returns CompletableFuture; block for the result.
            // Resilience4j (Retry + CB + TimeLimiter) is applied inside the call.
            CostAndUsageReportPage page = client.fetchPage(token).get();
            if(page==null || page.lineItems()==null){
                log.warn("AWS ingest: empty CUR response (token={}), skipping", token);
                return;
            }
            int persisted = ingestService.persistPage(page);
            log.info("AWS ingest: {} new of {} line item(s), nextToken={}",
                    persisted, page.lineItems().size(), page.nextToken());
        }
        catch (InterruptedException e){
            Thread.currentThread().interrupt();
            log.warn("AWS ingest poll interrupted");
        }
        catch (Exception e){
            log.warn("AWS ingest poll failed (retry next cycle): {}", e.toString());
        }
    }
}
