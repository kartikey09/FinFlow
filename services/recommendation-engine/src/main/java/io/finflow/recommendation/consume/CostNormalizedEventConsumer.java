package io.finflow.recommendation.consume;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.finflow.recommendation.domain.CostLineItemNormalized;
import io.finflow.recommendation.engine.RecommendationService;
import io.finflow.recommendation.store.OndemandSpendRollup;
import io.finflow.recommendation.store.OndemandSpendRollupKey;
import io.finflow.recommendation.store.OndemandSpendRollupRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Consumes finflow.events.cost-normalized to feed the PURCHASE_COMMITMENT rule.
 *
 * <p>Only ON-DEMAND lines matter here: a cost line with a non-null commitmentId is
 * already covered by a commitment, so it can't motivate BUYING one. On-demand lines
 * (commitmentId == null) accumulate into the per-(account,service) rollup; after each
 * update we re-check whether the sustained-spend threshold has been crossed.
 *
 * <p>The rollup update and the purchase re-evaluation share this method's transaction,
 * and the re-evaluation opens its own inner transaction to append to the outbox — so a
 * recommendation and its event still commit atomically.
 */
@Component
public class CostNormalizedEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(CostNormalizedEventConsumer.class);

    private final OndemandSpendRollupRepository rollups;
    private final RecommendationService service;
    private final ObjectMapper json;
    private final String tenantId;

    public CostNormalizedEventConsumer(OndemandSpendRollupRepository rollups,
                                       RecommendationService service,
                                       ObjectMapper json,
                                       @Value("${finflow.tenant-id:default}") String tenantId) {
        this.rollups = rollups;
        this.service = service;
        this.json = json;
        this.tenantId = tenantId;
    }

    @KafkaListener(topics = "finflow.events.cost-normalized")
    @Transactional
    public void onRecord(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            CostLineItemNormalized c = json.readValue(record.value(), CostLineItemNormalized.class);

            // Only uncommitted spend is a purchase signal.
            if (c.commitmentId() != null || c.accountId() == null || c.service() == null) {
                ack.acknowledge();
                return;
            }

            UUID eventId = EventEnvelope.id(record);
            OndemandSpendRollupKey key =
                    new OndemandSpendRollupKey(tenantId, c.accountId(), c.service());

            OndemandSpendRollup rollup = rollups.findById(key)
                    .orElseGet(() -> new OndemandSpendRollup(tenantId, c.accountId(), c.service()));

            // Exact-redelivery guard on the rollup so a replayed line isn't counted twice.
            if (eventId != null && eventId.equals(rollup.getLastEventId())) {
                ack.acknowledge();
                return;
            }

            String usageDay = c.usageStart() == null ? null
                    : (c.usageStart().length() >= 10 ? c.usageStart().substring(0, 10) : c.usageStart());
            rollup.addOndemandCost(c.vendor(), c.region(), c.costUsd(), usageDay, eventId);
            rollups.save(rollup);

            service.evaluatePurchase(rollup, eventId);
            ack.acknowledge();
        } catch (Exception e) {
            log.warn("Failed to handle cost-normalized record (offset={}): {}", record.offset(), e.toString());
            ack.acknowledge();
        }
    }
}
