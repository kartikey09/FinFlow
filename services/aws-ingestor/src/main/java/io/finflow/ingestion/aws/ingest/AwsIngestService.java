package io.finflow.ingestion.aws.ingest;

import io.finflow.ingestion.aws.client.CostAndUsageReportPage;
import io.finflow.ingestion.aws.client.CurLineItem;
import io.finflow.ingestion.aws.cursor.PollCursor;
import io.finflow.ingestion.aws.cursor.PollCursorRepository;
import io.finflow.outbox.OutboxAppender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * What this module does:
 * This service takes a downloaded page of AWS data and saves it to the database.
 * But it must do three distinct things at the exact same time:
 * 1. Save the raw AWS data (`cost_line_items_raw`).
 * 2. Publish an event to Kafka via the outbox (`outbox_event`).
 * 3. Update the pagination bookmark (`poll_cursor`).
 *
 * Why we built it this way (The Dual-Write Solution):
 * In a beginner system, if the server crashes after Step 1 but before Step 2,
 * you have "ghost data" sitting in the database that downstream services will
 * never know about.
 * * By wrapping this entire method in a single `@Transactional` block, we leverage
 * PostgreSQL's ACID guarantees. It is an "All-or-Nothing" operation. If the
 * server crashes at Step 2, Postgres automatically deletes the data from Step 1
 * (a Rollback). There is physically no state in the universe where a raw row
 * exists without its corresponding Kafka event.
 */

@Service
public class AwsIngestService {

    private static final Logger log = LoggerFactory.getLogger(AwsIngestService.class);

    // so that cursor table knows which bookmark we are updating
    static final String SOURCE = "aws-cur";

    private final CostLineItemRawRepository rawRepository; // to save raw row
    private final PollCursorRepository cursorRepository;  // to save the bookmark
    private final OutboxAppender outboxAppender;  //to fire the Kafka event

    public AwsIngestService(CostLineItemRawRepository rawRepository, PollCursorRepository cursorRepository,
                            OutboxAppender outboxAppender){
        this.rawRepository = rawRepository;
        this.cursorRepository = cursorRepository;
        this.outboxAppender = outboxAppender;
    }

    /// cursor read
    // THE OPTIMIZATION: Notice there is NO @Transactional annotation here.
    // Opening a database transaction is expensive. Because we are just doing a
    // single, fast read to get the token, we do not need transaction overhead.
    public String currentToken(){
        return cursorRepository.findById(SOURCE)
                .map(PollCursor::getLastToken)
                .orElse(null);
    }

    // The Transaction Boundary (The Most Important Line in the File)
    // When the Scheduler calls this method, Spring opens a Postgres transaction.
    // If ANY uncaught exception happens inside this method, Spring throws everything
    // away and rolls back the database.
    @Transactional
    public int persistPage(CostAndUsageReportPage page){
        int persisted = 0;
        // ingestion loop
        for(CurLineItem item : page.lineItems()){
            /// The Idempotency Gate (Duplicate Protection) -
            // If the row has no ID (bad data), OR if the bouncer (Repository) says
            // "I already have this ID in the database", we skip it completely using `continue`.
            // This allows the system to infinitely retry failed network pages
            // without ever producing duplicate Kafka events.
            if(item.lineItemId() == null || rawRepository.existsById(item.lineItemId())){
                continue;
            }
            // Convert the network DTO into a database Entity and save it.
            rawRepository.save(CostLineItemRaw.from(item));

            /// The Outbox Event
            // Remember how OutboxAppender requires `@Transactional(Propagation.MANDATORY)`?
            // Because we opened a transaction at the top of this method, the Appender
            // happily joins it.
            // It routes the message to 'billing', uses the 'usageAccountId' as the
            // Kafka Partition Key, tags it as a 'RawCostLineItem', and dumps the
            // raw JSON payload into the database.
            outboxAppender.append("billing", item.usageAccountId(), "RawCostLineItem", item);
            persisted++;
        }
        /// Advance the Bookmark
        // Only AFTER successfully saving all new rows and outbox events do we
        // update the cursor. If the database crashes here, the whole transaction
        // rolls back, meaning we "forget" we downloaded this page, ensuring we
        // naturally retry it next time.
        advanceCursor(page.nextToken());
        return persisted;
    }

    private void advanceCursor(String nextToken){
        // Find the existing cursor. If this is the very first time the app has
        // ever run, it creates a brand new one using `orElseGet`.
        PollCursor cursor = cursorRepository.findById(SOURCE)
                .orElseGet(() -> new PollCursor(SOURCE));
        // Update the token and the audit timestamp and save it back to the db
        cursor.setLastToken(nextToken);
        cursor.setUpdatedAt(OffsetDateTime.now());
        cursorRepository.save(cursor);
    }
}
