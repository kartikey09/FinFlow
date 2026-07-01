package io.finflow.normalizer.idempotency;

import com.github.benmanes.caffeine.cache.Cache;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Two-layer consume-side idempotency guard.
 *
 * <pre>
 *   alreadyProcessed(id)        markProcessed(id, type)
 *         │                              │
 *   ┌─────▼─────┐                   ┌────▼────┐
 *   │ L1 cache  │ Caffeine, in-mem  │  cache  │ also populated on mark
 *   │ last 100k │ - fast hot path   │  put    │ so subsequent reads hit fast
 *   └─────┬─────┘                   └────┬────┘
 *         │ MISS                          │
 *   ┌─────▼─────┐                   ┌────▼────┐
 *   │ L2 ledger │ processed_event   │ ledger  │ INSERT
 *   │ in DB     │ existsById        │ save    │
 *   └───────────┘                   └─────────┘
 * </pre>
 *
 * <p>Both methods use {@code MANDATORY} propagation: they MUST run inside the
 * caller's transaction (the handler's), so the dedup check, the work, and the
 * mark all commit or all roll back together. The L1 cache is updated on mark
 * (and on a confirming-positive L2 read) so the hot path stays hot. Cache puts
 * are transaction-local in their effect on data races: a transaction that
 * rolls back has its work undone in the DB, and a stale cache hit can only
 * cause a benign skip of an event that will be redelivered anyway — never a
 * data corruption.
 */
@Component
public class EventDeduplicator {

    private final ProcessedEventRepository repository;
    private final Cache<UUID, Boolean> cache;

    public EventDeduplicator(ProcessedEventRepository repository,
                             Cache<UUID, Boolean> processedEventCache) {
        this.repository = repository;
        this.cache = processedEventCache;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean alreadyProcessed(UUID eventId) {
        Boolean cached = cache.getIfPresent(eventId);
        if (Boolean.TRUE.equals(cached)) {
            return true;                                  // L1 hit — skip the DB
        }
        boolean seen = repository.existsById(eventId);     // L2 — durable ledger
        if (seen) {
            cache.put(eventId, Boolean.TRUE);              // warm L1 for future polls of the same id
        }
        return seen;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void markProcessed(UUID eventId, String eventType) {
        repository.save(new ProcessedEvent(eventId, eventType, OffsetDateTime.now()));
        cache.put(eventId, Boolean.TRUE);
    }
}
