package io.finflow.recommendation.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Consumer failure policy, identical in shape to cost-normalizer's: retry a transient
 * failure a few times, then publish the poison record to a {@code .DLT} topic and move
 * on so one bad event can't wedge the partition.
 *
 * <p>Note the consumers here also catch-and-ack internally, so this handler mainly
 * governs failures OUTSIDE the try (deserialization wiring, etc.). Belt and braces.
 */
@Configuration
public class KafkaErrorHandlingConfig {

    private static final long MAX_RETRIES = 3L;
    private static final long RETRY_INTERVAL_MS = 2_000L;

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate);
        FixedBackOff backOff = new FixedBackOff(RETRY_INTERVAL_MS, MAX_RETRIES);
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        handler.addNotRetryableExceptions(IllegalArgumentException.class);
        return handler;
    }
}
