package io.finflow.saga.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.finflow.saga.event.SagaEvent;
import io.finflow.saga.orchestration.SagaOrchestrationService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Consumes {@code saga.events} — result events from the adapter workers (or
 * hand-injected events during Day 17 testing).
 *
 * <p>Manual ack, offset committed AFTER the transactional handler returns.
 * Because {@code SagaOrchestrationService.handleEvent} is
 * {@code @Transactional}, this is the standard exactly-once shape: any crash
 * before ack replays the event; the pure {@code evaluate} function decides
 * whether the replay is a real transition or a no-op.
 *
 * <p>Note on error handling: unlike cost-normalizer (Day 10), this listener
 * does NOT install a DLT-tolerant error handler yet. That's Day 20's job (the
 * production-readiness pass for the saga). For today, an exception during
 * handling means the offset isn't committed and Kafka redelivers — which is
 * fine for a Chaos-API-injected 503 (the transition succeeds on retry) and
 * exactly what we want for optimistic-lock retries.
 */
@Component
public class SagaEventListener {

    private static final Logger log = LoggerFactory.getLogger(SagaEventListener.class);

    private final ObjectMapper objectMapper;
    private final SagaOrchestrationService orchestrationService;

    public SagaEventListener(ObjectMapper objectMapper,
                             SagaOrchestrationService orchestrationService) {
        this.objectMapper = objectMapper;
        this.orchestrationService = orchestrationService;
    }

    @KafkaListener(topics = "saga.events")
    public void onRecord(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            SagaEventPayload payload = objectMapper.readValue(record.value(), SagaEventPayload.class);
            SagaEvent event = payload.success()
                    ? new SagaEvent.StepSucceeded(payload.sagaId(), payload.step())
                    : new SagaEvent.StepFailed(payload.sagaId(), payload.step(), payload.reason());

            orchestrationService.handleEvent(event);
            ack.acknowledge();
        } catch (Exception e) {
            log.warn("Failed to handle saga.events record (offset={}): {}",
                    record.offset(), e.toString());
            // Intentionally do NOT ack — exception propagates, Kafka redelivers, and
            // either the transient issue clears or the optimistic-lock race resolves.
            // Day 20 will layer bounded retries + DLT on top of this baseline.
            throw new RuntimeException(e);
        }
    }
}
