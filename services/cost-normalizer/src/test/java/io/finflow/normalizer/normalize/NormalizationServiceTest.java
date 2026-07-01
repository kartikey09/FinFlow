package io.finflow.normalizer.normalize;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.finflow.normalizer.consumer.ConsumedEvent;
import io.finflow.normalizer.normalize.dispatch.RawPayloadDispatcher;
import io.finflow.outbox.OutboxAppender;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit-level orchestration check (no Spring, no DB). Three things matter:
 *   1. AWS payload routes to AwsCurNormalizer; ledger save; outbox append.
 *   2. GCP payload routes to GcpBillingNormalizer; ledger save; outbox append.
 *   3. UNIQUE-constraint violation on ledger save -> log + drop, NO outbox append.
 *      (The plan's "L2 dedup gate" behavior.)
 */
class NormalizationServiceTest {

    private final RawPayloadDispatcher dispatcher = mock(RawPayloadDispatcher.class);
    private final AwsCurNormalizer aws = mock(AwsCurNormalizer.class);
    private final GcpBillingNormalizer gcp = mock(GcpBillingNormalizer.class);
    private final CostLedgerRepository ledger = mock(CostLedgerRepository.class);
    private final OutboxAppender outbox = mock(OutboxAppender.class);
    private final ObjectMapper mapper = new ObjectMapper();

    private final NormalizationService service =
            new NormalizationService(dispatcher, aws, gcp, ledger, outbox, mapper, "default");

    @Test
    void awsEventIsNormalizedPersistedAndEmitted() {
        ConsumedEvent e = new ConsumedEvent(UUID.randomUUID(),
                "RawCostLineItem", "billing", "{\"identity/LineItemId\":\"x\"}");
        CostLineItemNormalized canonical = sample("AWS", "acct-1");
        when(dispatcher.classify(e.payload())).thenReturn(RawPayloadDispatcher.Vendor.AWS);
        when(aws.normalize(e.id(), "default", e.payload())).thenReturn(canonical);

        service.normalize(e);

        verify(ledger, times(1)).save(any(CostLedgerEntry.class));
        verify(outbox, times(1)).append(eq("cost-normalized"), eq("acct-1"),
                eq("CostLineItemNormalized"), eq(canonical));
        verify(gcp, never()).normalize(any(), any(), any());
    }

    @Test
    void gcpEventIsNormalizedPersistedAndEmitted() {
        ConsumedEvent e = new ConsumedEvent(UUID.randomUUID(),
                "RawCostLineItem", "billing", "{\"service\":{\"id\":\"x\"}}");
        CostLineItemNormalized canonical = sample("GCP", "acct-7");
        when(dispatcher.classify(e.payload())).thenReturn(RawPayloadDispatcher.Vendor.GCP);
        when(gcp.normalize(e.id(), "default", e.payload())).thenReturn(canonical);

        service.normalize(e);

        verify(ledger, times(1)).save(any(CostLedgerEntry.class));
        verify(outbox, times(1)).append(eq("cost-normalized"), eq("acct-7"),
                eq("CostLineItemNormalized"), eq(canonical));
        verify(aws, never()).normalize(any(), any(), any());
    }

    @Test
    void uniqueViolationOnLedgerIsLoggedAndDropped_NoOutboxAppend() {
        ConsumedEvent e = new ConsumedEvent(UUID.randomUUID(),
                "RawCostLineItem", "billing", "{\"identity/LineItemId\":\"x\"}");
        when(dispatcher.classify(e.payload())).thenReturn(RawPayloadDispatcher.Vendor.AWS);
        when(aws.normalize(e.id(), "default", e.payload())).thenReturn(sample("AWS", "acct-1"));
        when(ledger.save(any(CostLedgerEntry.class)))
                .thenThrow(new DataIntegrityViolationException("uq_cost_ledger_tenant_event"));

        service.normalize(e);   // must NOT throw

        verify(outbox, never()).append(any(), any(), any(), any());
    }

    @Test
    void unknownVendorIsDroppedSilently() {
        ConsumedEvent e = new ConsumedEvent(UUID.randomUUID(),
                "RawCostLineItem", "billing", "{\"foo\":\"bar\"}");
        when(dispatcher.classify(e.payload())).thenReturn(RawPayloadDispatcher.Vendor.UNKNOWN);

        service.normalize(e);

        verify(ledger, never()).save(any());
        verify(outbox, never()).append(any(), any(), any(), any());
    }

    private static CostLineItemNormalized sample(String vendor, String account) {
        return new CostLineItemNormalized(
                UUID.randomUUID(), "default", vendor, account, "platform",
                "Service", null, "us-east-1",
                "2025-11-01T00:00:00Z", "2025-11-01T01:00:00Z",
                1.0, 0.1, 0.12, null, "Usage", vendor);
    }
}
