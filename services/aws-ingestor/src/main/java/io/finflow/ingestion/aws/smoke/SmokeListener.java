package io.finflow.ingestion.aws.smoke;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes the smoke topic and acks the waiting latch in SmokeService. This is
 * the "consume" half of the round-trip that proves the Kafka consumer path
 * (deserializer, listener container, group subscription) is wired correctly.
 */
@Component
public class SmokeListener {

    private static final Logger log = LoggerFactory.getLogger(SmokeListener.class);

    private final SmokeService smokeService;

    public SmokeListener(SmokeService smokeService) {
        this.smokeService = smokeService;
    }

    @KafkaListener(topics = SmokeService.TOPIC, groupId = "aws-ingestor-smoke")
    public void onMessage(SmokeMessage message) {
        log.info("[SMOKE] consumed id={}", message.id());
        smokeService.ack(message.id());
    }
}
