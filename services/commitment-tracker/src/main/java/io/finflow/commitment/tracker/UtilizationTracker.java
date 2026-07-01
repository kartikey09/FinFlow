package io.finflow.commitment.tracker;

import io.finflow.commitment.consumer.CostNormalizedEvent;
import io.finflow.commitment.domain.Commitment;
import io.finflow.commitment.domain.CommitmentRepository;
import io.finflow.commitment.domain.CommitmentUtilization;
import io.finflow.commitment.domain.CommitmentUtilizationKey;
import io.finflow.commitment.domain.CommitmentUtilizationRepository;
import io.finflow.commitment.event.CommitmentSaturated;
import io.finflow.commitment.threshold.ThresholdEvaluator;
import io.finflow.outbox.OutboxAppender;
import org.apache.kafka.common.header.Headers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

/**
 * The stateful core of Day 14. For one normalized cost event:
 *
 * <pre>
 *   1. Gate          skip if commitmentId == null (on-demand, not our concern)
 *   2. Resolve       look up the master Commitment; skip unknown (silent miss)
 *   3. Period        derive period_day = usageStart's UTC date
 *   4. Idempotency   if utilization row's last_event_id == this event id, skip
 *                    (Kafka redelivery during a retry — already folded)
 *   5. Fold          increment used_amount_usd; persist
 *   6. Saturation    if today's fraction >= 1.0, emit CommitmentSaturated
 *   7. Threshold     delegate to ThresholdEvaluator (under-utilization streak)
 * </pre>
 *
 * <p>All of this runs in one transaction. The outbox appends from steps 6/7
 * join via {@code @Transactional(MANDATORY)}, so the state update and the
 * outgoing alert commit atomically.
 *
 * <p>Why is idempotency here instead of a separate processed_event table (like
 * cost-normalizer has)? Because the state row already carries
 * {@code last_event_id} for free — checking it costs nothing on top of the
 * lookup we're doing anyway. We accept the (tiny) caveat that a redelivery
 * arriving AFTER a different event has updated the same (commitment, day)
 * cannot be detected and would double-count. For Day 14 the trade-off is
 * acceptable; if it ever bites, swap to a processed_event table.
 */
@Service
public class UtilizationTracker {

    private static final Logger log = LoggerFactory.getLogger(UtilizationTracker.class);

    private final CommitmentRepository commitmentRepository;
    private final CommitmentUtilizationRepository utilizationRepository;
    private final ThresholdEvaluator thresholdEvaluator;
    private final OutboxAppender outboxAppender;
    private final String tenantId;

    public UtilizationTracker(CommitmentRepository commitmentRepository,
                              CommitmentUtilizationRepository utilizationRepository,
                              ThresholdEvaluator thresholdEvaluator,
                              OutboxAppender outboxAppender,
                              @Value("${finflow.tenant-id:default}") String tenantId) {
        this.commitmentRepository = commitmentRepository;
        this.utilizationRepository = utilizationRepository;
        this.thresholdEvaluator = thresholdEvaluator;
        this.outboxAppender = outboxAppender;
        this.tenantId = tenantId;
    }

    @Transactional
    public void apply(CostNormalizedEvent event, Headers kafkaHeaders) {
        if (event.commitmentId() == null || event.commitmentId().isBlank()) {
            return;                                                  // on-demand — not tracked here
        }
        Optional<Commitment> commitmentOpt = commitmentRepository.findById(event.commitmentId());
        if (commitmentOpt.isEmpty()) {
            log.debug("Unknown commitmentId={} — skipping (commitments table not seeded for it)",
                    event.commitmentId());
            return;
        }
        Commitment commitment = commitmentOpt.get();
        LocalDate periodDay = derivePeriodDay(event.usageStart());
        if (periodDay == null) {
            log.warn("Event for {} had unparseable usageStart='{}', skipping",
                    event.commitmentId(), event.usageStart());
            return;
        }
        UUID eventId = extractEventId(kafkaHeaders);

        // Get-or-create the (commitment, day) row.
        CommitmentUtilizationKey key = new CommitmentUtilizationKey(commitment.getCommitmentId(), periodDay);
        CommitmentUtilization util = utilizationRepository.findById(key)
                .orElseGet(() -> new CommitmentUtilization(
                        commitment.getCommitmentId(), periodDay, commitment.getDailyAmountUsd()));

        // Idempotency: same event re-applied to the SAME (commitment, day) row.
        if (eventId != null && eventId.equals(util.getLastEventId())) {
            log.debug("Idempotent skip for event {} (commitment={}, day={})",
                    eventId, commitment.getCommitmentId(), periodDay);
            return;
        }

        util.increment(event.costUsd(), eventId);
        utilizationRepository.save(util);

        // -- saturation rule (per-day) --
        double fraction = util.utilizationFraction();
        if (fraction >= 1.0) {
            outboxAppender.append(
                    "commitment",
                    commitment.getCommitmentId(),
                    "CommitmentSaturated",
                    new CommitmentSaturated(
                            commitment.getCommitmentId(),
                            tenantId,
                            commitment.getVendor(),
                            commitment.getAccountId(),
                            periodDay,
                            util.getUsedAmountUsd(),
                            util.getAvailableUsd(),
                            fraction));
        }

        // -- under-utilization rule (consecutive-day streak) --
        thresholdEvaluator.evaluate(commitment, util);
    }

    /** Pull the source event's UUID out of the Kafka 'id' header (set by the EventRouter). */
    private UUID extractEventId(Headers headers) {
        var header = headers.lastHeader("id");
        if (header == null || header.value() == null) return null;
        String raw = new String(header.value(), StandardCharsets.UTF_8).trim();
        if (raw.length() >= 2 && raw.startsWith("\"") && raw.endsWith("\"")) {
            raw = raw.substring(1, raw.length() - 1);
        }
        try { return UUID.fromString(raw); } catch (Exception e) { return null; }
    }

    /** Parse an ISO-8601 usage start (e.g. "2025-11-01T00:00:00Z") to its UTC date. */
    static LocalDate derivePeriodDay(String usageStart) {
        if (usageStart == null || usageStart.isBlank()) return null;
        try {
            return OffsetDateTime.parse(usageStart).withOffsetSameInstant(ZoneOffset.UTC).toLocalDate();
        } catch (Exception e) {
            return null;
        }
    }
}
