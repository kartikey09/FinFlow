package io.finflow.commitment.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.finflow.commitment.tracker.UtilizationTracker;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Listens for {@code CostLineItemNormalized} events on
 * {@code finflow.events.cost-normalized} and hands each one to the
 * {@link UtilizationTracker}.
 *
 * <p>Same shape as cost-normalizer's listener (Day 9): manual ack, offset
 * committed AFTER handling, so a crash mid-process replays the same offset and
 * idempotency in the tracker decides what to do. The id-based de-dup happens
 * inside {@code UtilizationTracker} via the {@code last_event_id} column on the
 * utilization row — see the rationale there.
 */
@Component
public class CostNormalizedEventListener {

    private static final Logger log = LoggerFactory.getLogger(CostNormalizedEventListener.class);

    private final ObjectMapper objectMapper;
    private final UtilizationTracker tracker;

    public CostNormalizedEventListener(ObjectMapper objectMapper, UtilizationTracker tracker) {
        this.objectMapper = objectMapper;
        this.tracker = tracker;
    }

    @KafkaListener(topics = "finflow.events.cost-normalized")
    public void onRecord(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            CostNormalizedEvent event = objectMapper.readValue(record.value(), CostNormalizedEvent.class);
            tracker.apply(event, record.headers());
            ack.acknowledge();
        } catch (Exception e) {
            // Log and ack — a poison record should not block the partition.
            // (A real production path adds DLT here, as cost-normalizer does on Day 10.)
            log.warn("Failed to handle cost-normalized record (offset={}, partition={}): {}",
                    record.offset(), record.partition(), e.toString());
            ack.acknowledge();
        }
    }
}
