package io.finflow.normalizer.normalize;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.finflow.normalizer.consumer.ConsumedEvent;
import io.finflow.normalizer.normalize.dispatch.RawPayloadDispatcher;
import io.finflow.outbox.OutboxAppender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Day 13's heart. Orchestrates the full normalization for one consumed event,
 * inside the caller's transaction:
 *
 * <pre>
 *  ConsumedEvent (raw vendor JSON)
 *      │
 *      ▼  dispatch by payload shape (AWS-flat vs GCP-nested)
 *  AwsCurNormalizer / GcpBillingNormalizer  →  CostLineItemNormalized
 *      │
 *      ▼  write canonical row to normalizer.cost_ledger
 *  CostLedgerEntry.save()                         ← UNIQUE(tenant_id,event_id) = L2 dedup gate
 *      │
 *      ▼  emit downstream
 *  outboxAppender.append("billing", ..., "CostLineItemNormalized", normalized)
 *                                                 → finflow.events.cost-normalized
 * </pre>
 *
 * <p>This whole sequence runs inside the @Transactional opened by
 * {@code BillingEventHandler.handle}. {@code OutboxAppender.append} is
 * {@code @Transactional(MANDATORY)} so it joins this transaction, which means
 * the ledger row and the outbox event commit together — no window where one
 * exists without the other.
 *
 * <p>Duplicate handling: the UNIQUE constraint throws
 * {@code DataIntegrityViolationException} if (tenant, event_id) is already in
 * the ledger. We catch, log, and return without re-throwing — per the plan,
 * "on UNIQUE violation: log + drop, don't fail the consumer". Spring's
 * rollback-on-checked-exception isn't an issue because we catch the throwing
 * call inside this method.
 */
@Service
public class NormalizationService {

    private static final Logger log = LoggerFactory.getLogger(NormalizationService.class);

    private final RawPayloadDispatcher dispatcher;
    private final AwsCurNormalizer awsNormalizer;
    private final GcpBillingNormalizer gcpNormalizer;
    private final CostLedgerRepository ledgerRepository;
    private final OutboxAppender outboxAppender;
    private final ObjectMapper objectMapper;
    private final String tenantId;

    public NormalizationService(RawPayloadDispatcher dispatcher,
                                AwsCurNormalizer awsNormalizer,
                                GcpBillingNormalizer gcpNormalizer,
                                CostLedgerRepository ledgerRepository,
                                OutboxAppender outboxAppender,
                                ObjectMapper objectMapper,
                                @Value("${finflow.tenant-id:default}") String tenantId) {
        this.dispatcher = dispatcher;
        this.awsNormalizer = awsNormalizer;
        this.gcpNormalizer = gcpNormalizer;
        this.ledgerRepository = ledgerRepository;
        this.outboxAppender = outboxAppender;
        this.objectMapper = objectMapper;
        this.tenantId = tenantId;
    }

    public void normalize(ConsumedEvent event) {
        // Only react to billing events; ignore anything else routed to this listener.
        if (!"billing".equals(event.aggregateType())) {
            log.debug("Ignoring non-billing event id={} aggregateType={}",
                    event.id(), event.aggregateType());
            return;
        }

        RawPayloadDispatcher.Vendor vendor = dispatcher.classify(event.payload());
        CostLineItemNormalized normalized = switch (vendor) {
            case AWS -> awsNormalizer.normalize(event.id(), tenantId, event.payload());
            case GCP -> gcpNormalizer.normalize(event.id(), tenantId, event.payload());
            case UNKNOWN -> {
                log.warn("Unknown raw payload shape for event id={}, dropping", event.id());
                yield null;
            }
        };
        if (normalized == null) {
            return;
        }

        try {
            ledgerRepository.save(CostLedgerEntry.from(normalized));
        } catch (DataIntegrityViolationException dup) {
            // L2 dedup gate caught a duplicate that bypassed L1 + processed_event.
            log.warn("Duplicate ledger row for tenant={} event_id={} — dropping (plan: log + drop)",
                    tenantId, event.id());
            return;
        }

        // Routes to finflow.events.cost-normalized; keyed by accountId so all line items
        // for one account stay ordered downstream.
        outboxAppender.append("cost-normalized",
                normalized.accountId() == null ? "unknown" : normalized.accountId(),
                "CostLineItemNormalized",
                normalized);

        log.info("Normalized {} event id={} -> cost_usd={} team={} vendor={}",
                vendor, event.id(), normalized.costUsd(), normalized.team(), normalized.vendor());
    }
}
