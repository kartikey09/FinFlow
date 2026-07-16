package io.finflow.ingestion.aws.ingest;

import io.finflow.ingestion.aws.client.AwsCurClient;
import io.finflow.ingestion.aws.client.CostAndUsageReportPage;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The engine that drives AWS data ingestion. Wakes on a timer, asks the DB where it
 * left off, fetches the next page from the Chaos API, hands it off to be saved.
 *
 * <p><b>Why the network call is outside the transaction</b> — holding a DB
 * connection open across a 5s HTTP call exhausts the pool under load. So: fast read
 * (cursor) -> SLOW network call with NO transaction held -> fast write (persist,
 * transaction opens and closes instantly).
 *
 * <p><b>Day 21:</b> fetchPage returns a CompletableFuture so the TimeLimiter can
 * enforce a deadline; we block on .get() and retry next cycle on failure.
 *
 * <h2>Day 24: the ingest trace needs a ROOT, and Spring will not give you one</h2>
 *
 * <p>Spring Boot auto-instruments incoming HTTP requests and Kafka records — but
 * NOT {@code @Scheduled} methods. There is no observation around a scheduled tick.
 *
 * <p>That is a real problem here, not a cosmetic one. Without a root span:
 * <ul>
 *   <li>{@code client.fetchPage()} opens an HTTP client span with no parent — it
 *       becomes a root, and it CLOSES when the call returns;</li>
 *   <li>by the time {@code persistPage()} runs and the outbox row is appended,
 *       there is no current span at all, so {@code trace_parent} is written NULL;</li>
 *   <li>Debezium then publishes an event with no traceparent header, and
 *       cost-normalizer starts a fresh, unrelated trace.</li>
 * </ul>
 * The whole ingest pipeline shatters into disconnected fragments.
 *
 * <p>Wrapping the poll body in an Observation creates a root span that stays current
 * across BOTH the HTTP call and the outbox write. Now the fetch is a child of the
 * poll, the outbox row carries the poll's traceparent, and cost-normalizer /
 * commitment-tracker / query-api all hang off the same tree.
 *
 * <p>{@code observe(Runnable)} starts the span, opens its scope, runs the body, and
 * closes/stops on the way out — including on exception. The existing try/catch stays
 * inside, so a failed poll is still absorbed and retried next cycle; it just also
 * shows up in Jaeger as a failed span, which is the point.
 */
@Component
public class AwsIngestScheduler {

    private static final Logger log = LoggerFactory.getLogger(AwsIngestScheduler.class);

    private final AwsCurClient client;
    private final AwsIngestService ingestService;
    private final ObservationRegistry observationRegistry;   // Day 24

    public AwsIngestScheduler(AwsCurClient client,
                              AwsIngestService ingestService,
                              ObservationRegistry observationRegistry){
        this.client = client;
        this.ingestService = ingestService;
        this.observationRegistry = observationRegistry;
    }

    @Scheduled(
            fixedDelayString = "${finflow.aws.ingest.poll-interval:PT30S}",
            initialDelayString = "${finflow.aws.ingest.initial-delay:PT10S}")
    public void poll(){
        // Day 24 — root span for the whole ingest pipeline. Everything downstream
        // (HTTP fetch, outbox append, and every consumer Debezium feeds) becomes a
        // descendant of this.
        Observation.createNotStarted("finflow.ingest.poll", observationRegistry)
                .lowCardinalityKeyValue("vendor", "aws")
                .observe(this::doPoll);
    }

    /** The original poll body, unchanged. Runs inside the poll span. */
    private void doPoll(){
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
