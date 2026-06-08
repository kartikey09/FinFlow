package io.finflow.ingestion.aws.smoke;

import io.finflow.common.Topics;
import io.finflow.ingestion.aws.cursor.PollCursor;
import io.finflow.ingestion.aws.cursor.PollCursorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Dev-only Week-1 plumbing proof. A single publishAndAwait() call exercises the
 * WHOLE stack synchronously:
 *   - produces a message to Kafka (producer + JSON serializer),
 *   - waits on a latch the @KafkaListener counts down (consumer + deserializer),
 *   - upserts a poll_cursor row (JPA + the Flyway-created table).
 *
 * If it returns a latency, produce→consume→persist all work. Removed once the
 * real poll loop replaces it on Day 11.
 */
@Service
public class SmokeService {

    private static final Logger log = LoggerFactory.getLogger(SmokeService.class);
    public static final String TOPIC = Topics.SMOKE;
    private static final String SMOKE_SOURCE = "aws-cur";

    private final KafkaTemplate<String, Object> kafka;
    private final PollCursorRepository cursors;
    private final ConcurrentHashMap<String, CountDownLatch> pending = new ConcurrentHashMap<>();

    public SmokeService(KafkaTemplate<String, Object> kafka, PollCursorRepository cursors) {
        this.kafka = kafka;
        this.cursors = cursors;
    }

    /** Publish, wait for the round-trip, persist a cursor row. Returns latency in ms. */
    public long publishAndAwait(String payload) throws InterruptedException {
        String id = UUID.randomUUID().toString();
        CountDownLatch latch = new CountDownLatch(1);
        pending.put(id, latch);

        long start = System.nanoTime();
        kafka.send(TOPIC, id, new SmokeMessage(id, payload, SMOKE_SOURCE));
        log.info("[SMOKE] published id={} payload={}", id, payload);

        // prove a DB write against the Flyway-created table
        PollCursor cursor = cursors.findById(SMOKE_SOURCE).orElseGet(() -> new PollCursor(SMOKE_SOURCE));
        cursor.setLastToken(id);
        cursor.setUpdatedAt(OffsetDateTime.now());
        cursors.save(cursor);

        boolean consumed = latch.await(5, TimeUnit.SECONDS);
        pending.remove(id);
        if (!consumed) {
            throw new IllegalStateException("Kafka round-trip timed out for id=" + id);
        }
        long ms = Duration.ofNanos(System.nanoTime() - start).toMillis();
        log.info("[SMOKE] round-trip OK id={} {}ms", id, ms);
        return ms;
    }

    /** Called by the listener when a message comes back. */
    void ack(String id) {
        CountDownLatch latch = pending.get(id);
        if (latch != null) {
            latch.countDown();
        }
    }
}
