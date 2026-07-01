package io.finflow.normalizer.consumer;

import io.finflow.normalizer.idempotency.EventDeduplicator;
import io.finflow.normalizer.normalize.NormalizationService;
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
 * Day 13 replacement of the Day 9 handler test.
 *
 * <p>Three behaviors that matter:
 *   1. New event: dedup-check returns false → normalize → mark.
 *   2. Already-processed: dedup-check returns true → skip normalize AND skip mark.
 *   3. Concurrent mark race: dedup short-circuits FIRST so normalize is skipped,
 *      and an unexpected DataIntegrityViolation during mark would not be possible
 *      in this path. (The handler's catch is defensive belt-and-suspenders only.)
 */
class BillingEventHandlerTest {

    private final EventDeduplicator dedup = mock(EventDeduplicator.class);
    private final NormalizationService normalizer = mock(NormalizationService.class);
    private final BillingEventHandler handler = new BillingEventHandler(dedup, normalizer);

    @Test
    void newEventIsNormalizedThenMarked() {
        ConsumedEvent e = event();
        when(dedup.alreadyProcessed(e.id())).thenReturn(false);

        handler.handle(e);

        verify(normalizer, times(1)).normalize(e);
        verify(dedup, times(1)).markProcessed(e.id(), e.type());
    }

    @Test
    void alreadyProcessedEventIsSkipped() {
        ConsumedEvent e = event();
        when(dedup.alreadyProcessed(e.id())).thenReturn(true);

        handler.handle(e);

        verify(normalizer, never()).normalize(any());
        verify(dedup, never()).markProcessed(any(), any());
    }

    @Test
    void concurrentMarkRaceIsToleratedNotPropagated() {
        ConsumedEvent e = event();
        when(dedup.alreadyProcessed(e.id())).thenReturn(false);
        // Simulate: between our dedup-check and mark, another consumer thread marked it.
        org.mockito.Mockito.doThrow(new DataIntegrityViolationException("processed_event_pkey"))
                .when(dedup).markProcessed(eq(e.id()), any());

        handler.handle(e);   // must NOT throw

        verify(normalizer, times(1)).normalize(e);   // normalization already happened in OUR tx
    }

    private static ConsumedEvent event() {
        return new ConsumedEvent(UUID.randomUUID(),
                "RawCostLineItem", "billing", "{\"identity/LineItemId\":\"x\"}");
    }
}
