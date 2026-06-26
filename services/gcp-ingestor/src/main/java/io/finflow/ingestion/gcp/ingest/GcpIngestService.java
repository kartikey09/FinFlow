package io.finflow.ingestion.gcp.ingest;

import io.finflow.ingestion.gcp.client.GcpBillingExportPage;
import io.finflow.ingestion.gcp.client.GcpBillingRow;
import io.finflow.ingestion.gcp.cursor.PollCursor;
import io.finflow.ingestion.gcp.cursor.PollCursorRepository;
import io.finflow.outbox.OutboxAppender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * Turns a fetched GCP page into durable state and outbound events — the exact
 * same shape as {@code AwsIngestService}, which is the point of Day 12.
 *
 * <p>{@link #persistPage} does the raw write, the outbox append, and the cursor
 * advance in ONE transaction; {@code OutboxAppender.append} joins it via
 * {@code MANDATORY} propagation, so a row and its event are atomic.
 *
 * <p>The event uses the SAME {@code aggregate_type} ("billing"), type
 * ("RawCostLineItem"), and topic (finflow.events.billing) as AWS — the two
 * vendors are distinguished by the payload, which is unmistakably GCP-shaped
 * (nested service/sku/credits). The aggregate key is the billing account, so all
 * events for one account stay ordered.
 *
 * <p>Idempotency: {@code row_key} (a deterministic hash, since GCP rows have no
 * natural id) is the raw table PK, so re-polled rows are skipped.
 */
@Service
public class GcpIngestService {

    private static final Logger log = LoggerFactory.getLogger(GcpIngestService.class);

    /** Cursor key for this billing source. */
    static final String SOURCE = "gcp-billing";

    private final GcpCostLineItemRawRepository rawRepository;
    private final PollCursorRepository cursorRepository;
    private final OutboxAppender outboxAppender;

    public GcpIngestService(GcpCostLineItemRawRepository rawRepository,
                            PollCursorRepository cursorRepository,
                            OutboxAppender outboxAppender) {
        this.rawRepository = rawRepository;
        this.cursorRepository = cursorRepository;
        this.outboxAppender = outboxAppender;
    }

    /** The resumable poll position; a single read needs no surrounding transaction. */
    public String currentToken() {
        return cursorRepository.findById(SOURCE)
                .map(PollCursor::getLastToken)
                .orElse(null);
    }

    /** Persist one page atomically and return how many NEW rows were ingested. */
    @Transactional
    public int persistPage(GcpBillingExportPage page) {
        int persisted = 0;
        for (GcpBillingRow row : page.rows()) {
            String rowKey = row.rowKey();
            if (rawRepository.existsById(rowKey)) {
                continue;   // already ingested — idempotent skip
            }
            rawRepository.save(GcpCostLineItemRaw.from(row));
            // Same topic/type as AWS; keyed by the billing account that owns the spend.
            outboxAppender.append("billing", row.billingAccountId(), "RawCostLineItem", row);
            persisted++;
        }
        advanceCursor(page.nextPageToken());
        return persisted;
    }

    private void advanceCursor(String nextPageToken) {
        PollCursor cursor = cursorRepository.findById(SOURCE)
                .orElseGet(() -> new PollCursor(SOURCE));
        cursor.setLastToken(nextPageToken);
        cursor.setUpdatedAt(OffsetDateTime.now());
        cursorRepository.save(cursor);
    }
}
