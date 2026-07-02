package io.finflow.query.consumer;

import io.finflow.query.projection.commitment.CommitmentHealth;
import io.finflow.query.projection.commitment.CommitmentHealthKey;
import io.finflow.query.projection.commitment.CommitmentHealthRepository;
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
import java.util.UUID;

/**
 * Reacts to alert events on {@code finflow.events.commitment} by flipping the
 * corresponding {@link CommitmentHealth} row's {@code status} and stamping the
 * alert metadata.
 *
 * <p>The alert type comes from the {@code eventType} Kafka header (set by the
 * outbox Event Router). We only need the {@code aggregateId} (Kafka message
 * key = {@code commitment_id}) to know which projection row to update — we do
 * NOT need to parse the JSON payload, because the fine-grained figures
 * (utilization, expiry date) already live in the projection thanks to the
 * cost-event consumer and the seeded commitments table. So this consumer is
 * intentionally payload-agnostic: any future alert type can be handled by
 * adding it to the status map, no schema mirroring required.
 */
@Component
public class CommitmentEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(CommitmentEventConsumer.class);

    private final CommitmentHealthRepository healthRepository;
    private final String tenantId;

    public CommitmentEventConsumer(CommitmentHealthRepository healthRepository,
                                   @Value("${finflow.tenant-id:default}") String tenantId) {
        this.healthRepository = healthRepository;
        this.tenantId = tenantId;
    }

    @KafkaListener(topics = "finflow.events.commitment")
    @Transactional
    public void onRecord(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            String commitmentId = record.key();
            String eventType = header(record, "eventType");
            UUID eventId = uuidHeader(record, "id");

            String newStatus = statusFor(eventType);
            if (commitmentId == null || newStatus == null) {
                ack.acknowledge();
                return;
            }
            CommitmentHealthKey key = new CommitmentHealthKey(tenantId, commitmentId);
            CommitmentHealth row = healthRepository.findById(key)
                    .orElseGet(() -> new CommitmentHealth(tenantId, commitmentId));

            if (eventId != null && eventId.equals(row.getLastAlertEventId())) {
                ack.acknowledge();
                return;                 // exact redelivery of the same alert — skip
            }
            row.recordAlert(eventType, newStatus, eventId);
            healthRepository.save(row);
            ack.acknowledge();
        } catch (Exception e) {
            log.warn("Failed to project commitment record (offset={}): {}", record.offset(), e.toString());
            ack.acknowledge();
        }
    }

    /** Map an alert eventType to the projection status it produces. */
    public static String statusFor(String eventType) {
        if (eventType == null) return null;
        return switch (eventType) {
            case "CommitmentUnderutilized"  -> "UNDERUTILIZED";
            case "CommitmentSaturated"      -> "SATURATED";
            case "CommitmentExpiringSoon"   -> "EXPIRING_SOON";
            default                         -> null;
        };
    }

    private static String header(ConsumerRecord<String, String> r, String name) {
        Header h = r.headers().lastHeader(name);
        if (h == null || h.value() == null) return null;
        String raw = new String(h.value(), StandardCharsets.UTF_8).trim();
        if (raw.length() >= 2 && raw.startsWith("\"") && raw.endsWith("\"")) {
            raw = raw.substring(1, raw.length() - 1);
        }
        return raw;
    }

    private static UUID uuidHeader(ConsumerRecord<String, String> r, String name) {
        String s = header(r, name);
        if (s == null) return null;
        try { return UUID.fromString(s); } catch (Exception e) { return null; }
    }
}
