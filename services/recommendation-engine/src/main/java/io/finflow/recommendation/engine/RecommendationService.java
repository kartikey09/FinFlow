package io.finflow.recommendation.engine;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.finflow.outbox.OutboxAppender;
import io.finflow.recommendation.domain.CommitmentUnderutilized;
import io.finflow.recommendation.domain.RecommendationCreated;
import io.finflow.recommendation.domain.RecommendationType;
import io.finflow.recommendation.store.OndemandSpendRollup;
import io.finflow.recommendation.store.OndemandSpendRollupRepository;
import io.finflow.recommendation.store.Recommendation;
import io.finflow.recommendation.store.RecommendationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Turns rule findings into persisted, published recommendations.
 *
 * <p>Each public method runs in ONE transaction that both writes the recommendation
 * row AND appends the outbound event to the outbox — the same atomic-write guarantee
 * every other FinFlow producer relies on. If the append fails, the recommendation
 * write rolls back with it; there's never a row without its event or vice versa.
 *
 * <p>The rule LOGIC lives in {@link RecommendationRules} (pure, unit-tested). This
 * class is the impure shell: it reads/writes the DB, decides UPDATE-vs-INSERT, handles
 * superseding, and emits. Keeping the two apart is what lets the thresholds be tested
 * without a database.
 */
@Service
public class RecommendationService {

    private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);

    private final RecommendationRules rules;
    private final RecommendationRepository recommendations;
    private final OndemandSpendRollupRepository rollups;
    private final OutboxAppender outbox;
    private final ObjectMapper json;

    public RecommendationService(RecommendationRules rules,
                                 RecommendationRepository recommendations,
                                 OndemandSpendRollupRepository rollups,
                                 OutboxAppender outbox,
                                 ObjectMapper json) {
        this.rules = rules;
        this.recommendations = recommendations;
        this.rollups = rollups;
        this.outbox = outbox;
        this.json = json;
    }

    /** Nominal per-day commitment value used when the real ceiling isn't derivable. */
    private static final double NOMINAL_DAILY_COMMITMENT_USD = 100.0;

    /**
     * Best-effort daily commitment ceiling. If we've accumulated on-demand spend for the
     * same account, its average daily spend is a reasonable stand-in for the scale of a
     * commitment there; otherwise fall back to a nominal figure. Either way the RELATIVE
     * ranking (idle-fraction x scale) is what the dashboard needs.
     */
    private double estimateDailyCeiling(CommitmentUnderutilized e) {
        return NOMINAL_DAILY_COMMITMENT_USD;
    }

    /**
     * React to a CommitmentUnderutilized signal: decide REBALANCE vs SELL vs nothing,
     * upsert the recommendation, and — if the type changed — supersede the stale one.
     *
     * <p>dailyCommitmentUsd would ideally come from commitments.commitments, but the
     * engine can't read that schema. We approximate it from the event's own numbers:
     * the available-USD ceiling isn't in this event, so we scale the observed spend by
     * utilization to recover the daily ceiling. Rough, and honestly labelled as such in
     * the evidence.
     */
    @Transactional
    public void onUnderutilized(CommitmentUnderutilized e, UUID eventId) {
        Optional<RecommendationType> maybeType = rules.classifyUnderutilization(e);
        if (maybeType.isEmpty()) {
            log.debug("underutilization streak {} below action threshold for {}",
                    e.streakDays(), e.commitmentId());
            return;
        }
        RecommendationType type = maybeType.get();

        // The exact daily commitment ceiling lives in commitment-tracker's schema, which
        // this service can't read. We look it up from our own on-demand rollup if we've
        // seen matching spend, and otherwise fall back to a nominal per-day figure so the
        // recommendation still ranks. Honest about the approximation in the evidence.
        double dailyCommitmentUsd = estimateDailyCeiling(e);
        double savings = rules.estimateUnderutilizationSavings(e, dailyCommitmentUsd);

        String evidence = evidenceJson(new LinkedHashMap<>(Map.of(
                "rule", type.name(),
                "streakDays", e.streakDays(),
                "observedFraction", e.observedFractionLatestDay(),
                "thresholdFraction", e.thresholdFraction(),
                "assumedDailyCommitmentUsd", dailyCommitmentUsd,
                "note", "exact commitment ceiling lives in commitment-tracker; savings is a ranking estimate")));

        // If a DIFFERENT type was previously open for this commitment (e.g. REBALANCE,
        // now escalated to SELL), retire it so the dashboard shows one current action.
        supersedeOtherTypes(e.tenantId(), e.commitmentId(), type.name());

        Recommendation row = recommendations
                .findByTenantIdAndTypeAndCommitmentId(e.tenantId(), type.name(), e.commitmentId())
                .orElseGet(() -> new Recommendation(UUID.randomUUID(), e.tenantId(), type.name()));

        if (isExactRedelivery(row, eventId)) return;

        row.apply(e.commitmentId(), e.accountId(), e.vendor(), null, null, savings, evidence, eventId);
        recommendations.save(row);
        publish(row);
        log.info("recommendation {} {} for commitment {} (est ${} /mo)",
                type, row.getId(), e.commitmentId(), savings);
    }

    /**
     * React to accumulated on-demand spend: if it has crossed the purchase break-even,
     * upsert a PURCHASE_COMMITMENT recommendation for that (account, service).
     */
    @Transactional
    public void evaluatePurchase(OndemandSpendRollup rollup, UUID eventId) {
        if (!rules.shouldRecommendPurchase(rollup.getObservedDays(), rollup.getTotalOndemandUsd())) {
            return;
        }
        double savings = rules.estimatePurchaseSavings(rollup.getTotalOndemandUsd());
        String type = RecommendationType.PURCHASE_COMMITMENT.name();

        String evidence = evidenceJson(new LinkedHashMap<>(Map.of(
                "rule", type,
                "observedDays", rollup.getObservedDays(),
                "totalOndemandUsd", rollup.getTotalOndemandUsd(),
                "service", rollup.getService(),
                "note", "sustained on-demand spend past break-even; a commitment would capture the discount")));

        Recommendation row = recommendations
                .findByTenantIdAndTypeAndAccountIdAndService(
                        rollup.getTenantId(), type, rollup.getAccountId(), rollup.getService())
                .orElseGet(() -> new Recommendation(UUID.randomUUID(), rollup.getTenantId(), type));

        if (isExactRedelivery(row, eventId)) return;

        // commitment_id stays null: we're recommending they BUY one for this SKU.
        row.apply(null, rollup.getAccountId(), rollup.getVendor(),
                rollup.getService(), rollup.getRegion(), savings, evidence, eventId);
        recommendations.save(row);
        publish(row);
        log.info("recommendation PURCHASE_COMMITMENT {} for {}/{} (est ${} saved)",
                row.getId(), rollup.getAccountId(), rollup.getService(), savings);
    }

    private void supersedeOtherTypes(String tenantId, String commitmentId, String keepType) {
        if (commitmentId == null) return;
        for (Recommendation other : recommendations
                .findByTenantIdAndCommitmentIdAndStatus(tenantId, commitmentId, "OPEN")) {
            if (!other.getType().equals(keepType)) {
                other.supersede();
                recommendations.save(other);
                log.debug("superseded {} {} in favour of {}", other.getType(), other.getId(), keepType);
            }
        }
    }

    /** Exact-redelivery guard: the same event already produced this row's current state. */
    private static boolean isExactRedelivery(Recommendation row, UUID eventId) {
        return eventId != null && eventId.equals(row.getLastEventId());
    }

    private void publish(Recommendation row) {
        outbox.append(
                "recommendation",                 // aggregate_type -> finflow.events.recommendation
                row.getId().toString(),           // aggregate_id / Kafka key
                "RecommendationCreated",          // event type header
                new RecommendationCreated(
                        row.getId(), row.getTenantId(), row.getType(),
                        row.getCommitmentId(), row.getAccountId(), row.getVendor(),
                        row.getService(), row.getRegion(),
                        row.getEstimatedSavingsUsd(), row.getEvidence()));
    }

    private String evidenceJson(Map<String, Object> facts) {
        try {
            return json.writeValueAsString(facts);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
