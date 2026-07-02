package io.finflow.adapter.aws.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.finflow.adapter.aws.execute.CommandExecutor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Kafka in-edge: parse one record, hand it to the transactional executor,
 * ack after success.
 *
 * <p>Manual ack after the executor returns. If the executor throws (a legitimate
 * failure that couldn't be recorded — e.g. the DB was unreachable), we DO NOT
 * ack; Kafka redelivers, and the next attempt goes through the dedup gate.
 * Business failures (a Chaos-injected 503 that Resilience4j exhausted) are
 * <i>successful</i> command-processing outcomes — a failure result event is
 * still published, so we ack normally.
 *
 * <p>Note on error handling: no DLT yet. Day 20 layers it on the same way it
 * exists on cost-normalizer (Day 10). For today, an exception during handling
 * means the offset stays uncommitted and Kafka redelivers — which is the right
 * default for genuinely transient DB issues.
 */
@Component
public class SagaCommandListener {

    private static final Logger log = LoggerFactory.getLogger(SagaCommandListener.class);

    private final ObjectMapper objectMapper;
    private final CommandExecutor executor;

    public SagaCommandListener(ObjectMapper objectMapper, CommandExecutor executor) {
        this.objectMapper = objectMapper;
        this.executor = executor;
    }

    @KafkaListener(topics = "saga.commands.aws")
    public void onRecord(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            SagaCommand command = objectMapper.readValue(record.value(), SagaCommand.class);
            executor.execute(command);
            ack.acknowledge();
        } catch (Exception e) {
            log.warn("Failed to handle saga.commands.aws record (offset={}, key={}): {}",
                    record.offset(), record.key(), e.toString());
            // Do NOT ack — let Kafka redeliver. The dedup gate protects side effects.
            throw new RuntimeException(e);
        }
    }
}
