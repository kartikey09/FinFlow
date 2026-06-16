package io.finflow.normalizer.idempotency;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * The consume-side idempotency guard, backed by the processed_event table.
 *
 * Both methods use MANDATORY propagation so they MUST run inside the caller's
 * transaction (BillingEventHandler.handle). That keeps the "have I seen this?"
 * check, the business processing, and the "mark handled" write in one atomic
 * unit: if processing fails, the mark rolls back too and the event is retried.
 *
 * (Designed for extraction into a shared/idempotency-starter once a second
 * consumer needs it — kept local while there is exactly one consumer.)
 */

@Component
public class EventDeduplicator {

    private final ProcessedEventRepository repository;

    public EventDeduplicator(ProcessedEventRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean alreadyProcessed(UUID eventId) {
        return repository.existsById(eventId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void markProcessed(UUID eventId, String eventType) {
        repository.save(new ProcessedEvent(eventId, eventType, OffsetDateTime.now()));
    }
}
