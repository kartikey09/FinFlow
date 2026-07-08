package io.finflow.adapter.gcp.config;

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
 * GCP mirror of aws-adapter-worker's KafkaErrorHandlingConfig. Same 3-retry
 * exponential backoff, same {@code finflow.dlq.<topic>} DLT naming, same
 * OptimisticLockingFailureException opt-out. Kept as a separate class per
 * the adapter-independence discipline established on Day 19.
 */
@Configuration
public class KafkaErrorHandlingConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaErrorHandlingConfig.class);

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
        backoff.setMaxElapsedTime(8_000L);

        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backoff);
        handler.addNotRetryableExceptions(
                org.springframework.dao.OptimisticLockingFailureException.class);
        return handler;
    }
}
