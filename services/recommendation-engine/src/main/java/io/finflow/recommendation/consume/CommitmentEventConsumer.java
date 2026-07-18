package io.finflow.recommendation.consume;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.finflow.recommendation.domain.CommitmentUnderutilized;
import io.finflow.recommendation.engine.RecommendationService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Consumes finflow.events.commitment. The recommendation-engine only acts on
 * {@code CommitmentUnderutilized} — that's the signal that a commitment is being
 * wasted, which drives REBALANCE and SELL. {@code CommitmentSaturated} and
 * {@code CommitmentExpiringSoon} are acknowledged and ignored (a saturated commitment
 * is healthy; expiry is commitment-tracker's alert, not a savings action here).
 *
 * <p>Manual ack, and a poison record is acknowledged rather than allowed to block the
 * partition — the same failure policy as query-api's consumers.
 */
@Component
public class CommitmentEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(CommitmentEventConsumer.class);

    private final RecommendationService service;
    private final ObjectMapper json;

    public CommitmentEventConsumer(RecommendationService service, ObjectMapper json) {
        this.service = service;
        this.json = json;
    }

    @KafkaListener(topics = "finflow.events.commitment")
    public void onRecord(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            String eventType = EventEnvelope.type(record);
            if (!"CommitmentUnderutilized".equals(eventType)) {
                ack.acknowledge();          // not a signal we act on
                return;
            }
            UUID eventId = EventEnvelope.id(record);
            CommitmentUnderutilized e = json.readValue(record.value(), CommitmentUnderutilized.class);
            service.onUnderutilized(e, eventId);
            ack.acknowledge();
        } catch (Exception ex) {
            log.warn("Failed to handle commitment record (offset={}): {}", record.offset(), ex.toString());
            ack.acknowledge();
        }
    }
}
