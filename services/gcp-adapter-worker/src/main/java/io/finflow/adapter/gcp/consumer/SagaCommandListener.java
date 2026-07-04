package io.finflow.adapter.gcp.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.finflow.adapter.gcp.execute.CommandExecutor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Kafka in-edge for {@code saga.commands.gcp}. Same pattern as the AWS
 * adapter's listener: parse, hand to the transactional executor, ack after
 * success. Business failures (Chaos-injected 503 that Resilience4j exhausted)
 * ARE successful command-processing outcomes — a failure event is still
 * published, so we ack normally. Only DB-unreachable-class exceptions cause
 * a non-ack + redelivery.
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

    @KafkaListener(topics = "saga.commands.gcp")
    public void onRecord(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            SagaCommand command = objectMapper.readValue(record.value(), SagaCommand.class);
            executor.execute(command);
            ack.acknowledge();
        } catch (Exception e) {
            log.warn("Failed to handle saga.commands.gcp record (offset={}, key={}): {}",
                    record.offset(), record.key(), e.toString());
            throw new RuntimeException(e);
        }
    }
}
