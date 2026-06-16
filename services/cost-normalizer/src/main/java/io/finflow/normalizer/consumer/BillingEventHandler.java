package io.finflow.normalizer.consumer;

import io.finflow.normalizer.idempotency.EventDeduplicator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Processes one consumed event idempotently, inside a single transaction:
 * check the dedup ledger, do the work, record the event id. If the work throws,
 * the whole transaction rolls back (including the dedup mark) and the event is
 * redelivered and retried.
 *
 * Day 9: "the work" is just logging — this proves the pipeline end to end.
 * Day 13 (the cost-normalizer's real logic) replaces the logging with the
 * actual normalization.
 */
@Service
public class BillingEventHandler {

    private static final Logger log = LoggerFactory.getLogger(BillingEventHandler.class);

    private final EventDeduplicator deduplicator;

    public BillingEventHandler(EventDeduplicator deduplicator) {
        this.deduplicator = deduplicator;
    }

    @Transactional
    public void handle(ConsumedEvent event) {
        if (deduplicator.alreadyProcessed(event.id())) {
            log.info("Skipping already-processed event id={} type={}", event.id(), event.type());
            return;
        }

        // --- Day 9 placeholder for real processing ---
        log.info("Processing event id={} type={} aggregateType={} payloadChars={}",
                event.id(), event.type(), event.aggregateType(),
                event.payload() == null ? 0 : event.payload().length());
        // --- end placeholder (Day 13 replaces this with normalization) ---

        deduplicator.markProcessed(event.id(), event.type());
    }
}
