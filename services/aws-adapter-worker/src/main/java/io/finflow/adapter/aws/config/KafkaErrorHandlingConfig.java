package io.finflow.adapter.aws.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

import java.util.HashMap;
import java.util.Map;

/**
 * Day 20: Dead-Letter Topic wiring for {@code saga.commands.aws}.
 *
 * <p>Parallels cost-normalizer's Day-10 DLT setup: bounded exponential retries
 * inside the listener, then republish the poison record to
 * {@code finflow.dlq.saga.commands.aws} with the original headers preserved
 * (including {@code kafka_dlt_exception_message}). An operator can inspect the
 * DLT to understand why a record couldn't be processed.
 *
 * <h2>What survives what</h2>
 *
 * <ul>
 *   <li>Chaos-injected 503s and 5s hangs — Resilience4j swallows them via
 *       Retry, then reports them as business FAILURE via a normal StepFailed
 *       event. The DLT is NOT the path for these.</li>
 *   <li>Transient DB unreachability during {@code CommandExecutor.execute} —
 *       exception propagates from the listener, Spring rolls back, the retry
 *       policy waits and re-invokes. The record is NEVER acked between
 *       attempts, so a hard crash mid-retry replays from the last commit.</li>
 *   <li>Poison records (unparseable JSON, unknown enum values) — surface as
 *       an exception on the first attempt; after all attempts fail identically
 *       the DLT publishes with headers and the offset advances so the poison
 *       doesn't wedge the partition.</li>
 * </ul>
 */
@Configuration
public class KafkaErrorHandlingConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaErrorHandlingConfig.class);

    /**
     * Producer used only by the DLT recoverer to republish poison records.
     * Kept separate from any application-level producer bean because it must
     * serialize to plain strings (that's what DeadLetterPublishingRecoverer
     * expects); we don't want it interfering with any KafkaTemplate a caller
     * might create for other purposes.
     */
    @Bean
    public KafkaTemplate<String, String> dltKafkaTemplate(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));
    }

    /**
     * Bounded retry + DLT recovery. Exponential backoff (250ms, 500ms, 1s, 2s,
     * 4s) means 3 retries stretched over ~8s — enough to ride out a
     * DB-restart-blip but bounded so a permanent poison record doesn't hang
     * a partition forever.
     */
    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> dltKafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                dltKafkaTemplate,
                (record, ex) -> {
                    log.warn("Publishing to DLT: topic={} offset={} error={}",
                            record.topic(), record.offset(), ex.toString());
                    return new org.apache.kafka.common.TopicPartition(
                            "finflow.dlq." + record.topic(), record.partition());
                });

        ExponentialBackOff backoff = new ExponentialBackOff(250L, 2.0);
        backoff.setMaxInterval(4_000L);
        backoff.setMaxElapsedTime(8_000L);   // 3 retries → give up, publish to DLT

        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backoff);
        // OptimisticLockingFailureException should NOT be retried by Kafka —
        // it's already meaningful (the record already succeeded elsewhere).
        // Let Kafka redeliver from consumer group instead.
        handler.addNotRetryableExceptions(
                org.springframework.dao.OptimisticLockingFailureException.class);
        return handler;
    }
}
