package io.finflow.normalizer.consumer;

import io.finflow.normalizer.idempotency.EventDeduplicator;
import io.finflow.normalizer.normalize.NormalizationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Processes one consumed event idempotently, in one transaction:
 *   dedup-check (L1 cache + L2 ledger) -> normalize + persist + outbox-append -> mark.
 *
 * <p>Day 9: "the work" was a log line. Day 13 replaces it with
 * {@link NormalizationService#normalize}: vendor dispatch, canonicalization,
 * cost_ledger insert, and the {@code CostLineItemNormalized} outbox append.
 *
 * <p>If processing throws, the whole transaction (including the dedup mark)
 * rolls back and the event is redelivered. The DLQ machinery from Day 10
 * handles bounded retry + dead-letter on persistent failure.
 */
@Service
public class BillingEventHandler {

    private static final Logger log = LoggerFactory.getLogger(BillingEventHandler.class);

    private final EventDeduplicator deduplicator;
    private final NormalizationService normalizationService;

    public BillingEventHandler(EventDeduplicator deduplicator,
                               NormalizationService normalizationService) {
        this.deduplicator = deduplicator;
        this.normalizationService = normalizationService;
    }

    @Transactional
    public void handle(ConsumedEvent event) {
        if (deduplicator.alreadyProcessed(event.id())) {
            log.info("Skipping already-processed event id={} type={}", event.id(), event.type());
            return;
        }

        normalizationService.normalize(event);

        try {
            deduplicator.markProcessed(event.id(), event.type());
        } catch (DataIntegrityViolationException race) {
            // A concurrent redelivery of the same event id marked it first; treat as already done.
            log.debug("Duplicate processed_event insert for id={} — concurrent redelivery, ignoring",
                    event.id());
        }
    }
}
