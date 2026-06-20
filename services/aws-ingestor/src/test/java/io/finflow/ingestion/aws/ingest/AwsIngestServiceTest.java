package io.finflow.ingestion.aws.ingest;

import io.finflow.ingestion.aws.client.CostAndUsageReportPage;
import io.finflow.ingestion.aws.client.CurLineItem;
import io.finflow.ingestion.aws.cursor.PollCursor;
import io.finflow.ingestion.aws.cursor.PollCursorRepository;
import io.finflow.outbox.OutboxAppender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test of the ingestion heart — no broker, no DB, no Docker. Proves the two
 * properties that matter: every NEW item is landed + emitted + the cursor moves,
 * and an already-seen item is skipped (idempotency gate).
 */
@ExtendWith(MockitoExtension.class)
class AwsIngestServiceTest {

    @Mock private CostLineItemRawRepository rawRepository;
    @Mock private PollCursorRepository cursorRepository;
    @Mock private OutboxAppender outboxAppender;

    @InjectMocks private AwsIngestService service;

    @Test
    void newItemsAreLandedAndEmittedThenCursorAdvances() {
        CurLineItem a = item("li-1", "acct-1");
        CurLineItem b = item("li-2", "acct-2");
        CostAndUsageReportPage page = new CostAndUsageReportPage("2025-11", List.of(a, b), "5");
        when(rawRepository.existsById(anyString())).thenReturn(false);
        when(cursorRepository.findById(AwsIngestService.SOURCE)).thenReturn(Optional.empty());

        int persisted = service.persistPage(page);

        assertThat(persisted).isEqualTo(2);
        verify(rawRepository, times(2)).save(any(CostLineItemRaw.class));
        verify(outboxAppender).append("billing", "acct-1", "RawCostLineItem", a);
        verify(outboxAppender).append("billing", "acct-2", "RawCostLineItem", b);

        ArgumentCaptor<PollCursor> saved = ArgumentCaptor.forClass(PollCursor.class);
        verify(cursorRepository).save(saved.capture());
        assertThat(saved.getValue().getLastToken()).isEqualTo("5");
    }

    @Test
    void alreadyIngestedItemsAreSkipped() {
        CurLineItem seen = item("li-1", "acct-1");
        CurLineItem fresh = item("li-2", "acct-2");
        // nextToken null = end of report; the gate still protects the loop-back re-poll.
        CostAndUsageReportPage page = new CostAndUsageReportPage("2025-11", List.of(seen, fresh), null);
        when(rawRepository.existsById("li-1")).thenReturn(true);
        when(rawRepository.existsById("li-2")).thenReturn(false);
        when(cursorRepository.findById(AwsIngestService.SOURCE)).thenReturn(Optional.empty());

        int persisted = service.persistPage(page);

        assertThat(persisted).isEqualTo(1);
        verify(rawRepository, times(1)).save(any(CostLineItemRaw.class));
        verify(outboxAppender, times(1)).append(eq("billing"), eq("acct-2"), eq("RawCostLineItem"), any());
        verify(outboxAppender, never()).append(eq("billing"), eq("acct-1"), anyString(), any());
    }

    private static CurLineItem item(String id, String account) {
        return new CurLineItem(
                id, "payer-0", account, "Usage",
                "2025-11-01T00:00:00Z", "2025-11-01T01:00:00Z",
                "AmazonEC2", "BoxUsage:t3.medium", 0.0464, "us-east-1",
                null, "platform");
    }
}
