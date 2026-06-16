package io.finflow.normalizer.consumer;

import io.finflow.normalizer.idempotency.EventDeduplicator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test of the idempotency decision: a seen event is skipped (no mark), a
 * new event is processed and marked. Mockito only — no Docker, no Spring.
 * (Calling handle() directly bypasses the @Transactional proxy, which is fine
 * for testing the branching logic; the transactional contract is covered by
 * EventDeduplicatorIT.)
 */
@ExtendWith(MockitoExtension.class)
class BillingEventHandlerTest {

    @Mock
    EventDeduplicator deduplicator;

    @InjectMocks
    BillingEventHandler handler;

    @Test
    void skipsEventThatWasAlreadyProcessed() {
        UUID id = UUID.randomUUID();
        when(deduplicator.alreadyProcessed(id)).thenReturn(true);

        handler.handle(new ConsumedEvent(id, "RawBillingPagePulled", "billing", "{}"));

        verify(deduplicator, never()).markProcessed(any(), any());
    }

    @Test
    void processesAndMarksANewEvent() {
        UUID id = UUID.randomUUID();
        when(deduplicator.alreadyProcessed(id)).thenReturn(false);

        handler.handle(new ConsumedEvent(id, "RawBillingPagePulled", "billing", "{\"k\":\"v\"}"));

        verify(deduplicator).markProcessed(id, "RawBillingPagePulled");
    }
}
