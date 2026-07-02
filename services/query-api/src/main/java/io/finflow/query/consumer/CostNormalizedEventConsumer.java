package io.finflow.query.consumer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.finflow.query.projection.commitment.CommitmentHealth;
import io.finflow.query.projection.commitment.CommitmentHealthKey;
import io.finflow.query.projection.commitment.CommitmentHealthRepository;
import io.finflow.query.projection.spend.SpendByTeamDay;
import io.finflow.query.projection.spend.SpendByTeamDayKey;
import io.finflow.query.projection.spend.SpendByTeamDayRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Folds each {@code CostLineItemNormalized} event into BOTH projections:
 * the spend-by-team-day chart data AND the utilization side of the
 * commitment-health table.
 *
 * <p>Why one consumer feeding two tables? Because both derivations come from
 * the same source event. Splitting into two consumers would consume Kafka
 * twice; a single {@code @Transactional} handler updates both in one go and
 * the projections stay in step.
 *
 * <p>Idempotency is per-projection-row: each row carries a {@code last_event_id}
 * (spend) or {@code last_alert_event_id} (health, for alerts only). This is
 * the same cheap in-row trick Day 14 uses.
 */
@Component
public class CostNormalizedEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(CostNormalizedEventConsumer.class);

    private final SpendByTeamDayRepository spendRepository;
    private final CommitmentHealthRepository healthRepository;
    private final ObjectMapper objectMapper;
    private final String tenantId;

    public CostNormalizedEventConsumer(SpendByTeamDayRepository spendRepository,
                                       CommitmentHealthRepository healthRepository,
                                       ObjectMapper objectMapper,
                                       @Value("${finflow.tenant-id:default}") String tenantId) {
        this.spendRepository = spendRepository;
        this.healthRepository = healthRepository;
        this.objectMapper = objectMapper;
        this.tenantId = tenantId;
    }

    @KafkaListener(topics = "finflow.events.cost-normalized")
    @Transactional
    public void onRecord(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            NormalizedPayload p = objectMapper.readValue(record.value(), NormalizedPayload.class);
            UUID eventId = extractEventId(record);
            updateSpendProjection(p, eventId);
            updateHealthProjection(p);
            ack.acknowledge();
        } catch (Exception e) {
            log.warn("Failed to project cost-normalized record (offset={}): {}", record.offset(), e.toString());
            ack.acknowledge();          // poison record must not block the partition
        }
    }

    private void updateSpendProjection(NormalizedPayload p, UUID eventId) {
        if (p.team() == null || p.costUsd() == null || p.usageStart() == null) return;
        LocalDate day = toUtcDate(p.usageStart());
        if (day == null) return;

        SpendByTeamDayKey key = new SpendByTeamDayKey(tenantId, p.team(), day);
        SpendByTeamDay row = spendRepository.findById(key)
                .orElseGet(() -> new SpendByTeamDay(tenantId, p.team(), day));

        if (eventId != null && eventId.equals(row.getLastEventId())) {
            return;                     // exact-same redelivery to exact-same row — skip
        }
        row.addSpend(p.costUsd(), eventId);
        spendRepository.save(row);
    }

    private void updateHealthProjection(NormalizedPayload p) {
        // Only cost events tagged with a commitment update the health row's
        // utilization figures; on-demand events don't touch commitment health.
        if (p.commitmentId() == null || p.commitmentId().isBlank()) return;
        LocalDate day = toUtcDate(p.usageStart());

        CommitmentHealthKey key = new CommitmentHealthKey(tenantId, p.commitmentId());
        CommitmentHealth row = healthRepository.findById(key)
                .orElseGet(() -> new CommitmentHealth(tenantId, p.commitmentId()));

        // "Latest" is naive here — we assume events arrive roughly in order and
        // update the projection with each one. In a strict-ordering world we'd
        // compare against latestPeriodDay; in the demo, this is fine.
        row.updateUtilization(p.vendor(), p.accountId(), day, p.costUsd(), row.getLatestAvailableUsd());
        healthRepository.save(row);
    }

    private static LocalDate toUtcDate(String iso) {
        try { return OffsetDateTime.parse(iso).withOffsetSameInstant(ZoneOffset.UTC).toLocalDate(); }
        catch (Exception e) { return null; }
    }

    private static UUID extractEventId(ConsumerRecord<String, String> record) {
        Header h = record.headers().lastHeader("id");
        if (h == null || h.value() == null) return null;
        String raw = new String(h.value(), StandardCharsets.UTF_8).trim();
        if (raw.length() >= 2 && raw.startsWith("\"") && raw.endsWith("\"")) {
            raw = raw.substring(1, raw.length() - 1);
        }
        try { return UUID.fromString(raw); } catch (Exception e) { return null; }
    }

    /**
     * Minimal mirror of the canonical CostLineItemNormalized shape — only the
     * fields query-api reads. Additive schema changes upstream never break us
     * because unknown properties are ignored.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record NormalizedPayload(
            String tenantId,
            String vendor,
            String accountId,
            String team,
            String commitmentId,
            String usageStart,
            Double costUsd
    ) {}
}
