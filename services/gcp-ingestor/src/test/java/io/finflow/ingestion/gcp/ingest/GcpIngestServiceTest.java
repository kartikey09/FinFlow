package io.finflow.ingestion.gcp.ingest;

import io.finflow.ingestion.gcp.client.GcpBillingExportPage;
import io.finflow.ingestion.gcp.client.GcpBillingRow;
import io.finflow.ingestion.gcp.cursor.PollCursor;
import io.finflow.ingestion.gcp.cursor.PollCursorRepository;
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
 * Unit test of the GCP ingestion heart — no broker, no DB, no Docker. Same two
 * properties as the AWS test: new rows are landed + emitted + the cursor moves,
 * and an already-seen row (by deterministic row_key) is skipped.
 */
@ExtendWith(MockitoExtension.class)
class GcpIngestServiceTest {

    @Mock private GcpCostLineItemRawRepository rawRepository;
    @Mock private PollCursorRepository cursorRepository;
    @Mock private OutboxAppender outboxAppender;

    @InjectMocks private GcpIngestService service;

    @Test
    void newRowsAreLandedAndEmittedThenCursorAdvances() {
        GcpBillingRow a = row("acct-1", "svc-a", "sku-a", "proj-1", 100.0, 0.012);
        GcpBillingRow b = row("acct-1", "svc-b", "sku-b", "proj-2", 50.0, 0.012);
        GcpBillingExportPage page = new GcpBillingExportPage(List.of(a, b), "4");
        when(rawRepository.existsById(anyString())).thenReturn(false);
        when(cursorRepository.findById(GcpIngestService.SOURCE)).thenReturn(Optional.empty());

        int persisted = service.persistPage(page);

        assertThat(persisted).isEqualTo(2);
        verify(rawRepository, times(2)).save(any(GcpCostLineItemRaw.class));
        verify(outboxAppender).append("billing", "acct-1", "RawCostLineItem", a);
        verify(outboxAppender).append("billing", "acct-1", "RawCostLineItem", b);

        ArgumentCaptor<PollCursor> saved = ArgumentCaptor.forClass(PollCursor.class);
        verify(cursorRepository).save(saved.capture());
        assertThat(saved.getValue().getLastToken()).isEqualTo("4");
    }

    @Test
    void alreadyIngestedRowsAreSkipped() {
        GcpBillingRow seen = row("acct-1", "svc-a", "sku-a", "proj-1", 100.0, 0.012);
        GcpBillingRow fresh = row("acct-2", "svc-b", "sku-b", "proj-2", 50.0, 0.012);
        GcpBillingExportPage page = new GcpBillingExportPage(List.of(seen, fresh), null);
        when(rawRepository.existsById(seen.rowKey())).thenReturn(true);
        when(rawRepository.existsById(fresh.rowKey())).thenReturn(false);
        when(cursorRepository.findById(GcpIngestService.SOURCE)).thenReturn(Optional.empty());

        int persisted = service.persistPage(page);

        assertThat(persisted).isEqualTo(1);
        verify(rawRepository, times(1)).save(any(GcpCostLineItemRaw.class));
        verify(outboxAppender, times(1)).append(eq("billing"), eq("acct-2"), eq("RawCostLineItem"), any());
        verify(outboxAppender, never()).append(eq("billing"), eq("acct-1"), anyString(), any());
    }

    private static GcpBillingRow row(String account, String svc, String sku, String project,
                                     double cost, double rate) {
        return new GcpBillingRow(
                account,
                new GcpBillingRow.Service(svc, svc + "-desc"),
                new GcpBillingRow.Sku(sku, sku + "-desc"),
                "2025-11-01T00:00:00Z", "2025-11-01T01:00:00Z",
                new GcpBillingRow.Project(project, project + "-name"),
                List.of(),
                cost, "INR", rate,
                new GcpBillingRow.Usage(10.0, "hour", 10.0, "hour"),
                List.of(new GcpBillingRow.Credit("cud-1", -12.0, GcpBillingRow.COMMITTED_USAGE_DISCOUNT)),
                "regular");
    }
}
