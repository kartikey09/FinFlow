package io.finflow.normalizer.config;

// Pulling in Spring's core configuration annotations and the specific Kafka
// libraries required to intercept errors, publish messages back out, and define wait times
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * What this module does:
 * This class configures the consumer-level failure policy for your Spring Kafka
 * application (the cost-normalizer). It acts as a safety valve. If a message
 * from the 'finflow.events.*' topic causes your Java code to throw an exception,
 * this configuration decides exactly what happens next.
 *
 * How it works:
 * By default, Kafka consumers operate on "at-least-once" delivery. If your
 * application crashes while processing a message (e.g., a database lock or bad
 * JSON), it never acknowledges the message. Kafka then hands the exact same
 * broken message back to your application. Without this class, your app would
 * crash endlessly, blocking the partition and completely stopping the pipeline.
 *
 * This configuration changes that behavior by implementing a 3-step policy:
 * 1. RETRY: If it's a transient error (like a momentary Postgres connection
 * blip), wait 2 seconds and try again, up to 3 times.
 * 2. QUARANTINE: If it fails 3 times, take the message, publish it to a
 * "Dead Letter Topic" (DLT), and tell Kafka to move on to the next message.
 * 3. SHORT-CIRCUIT: If the error is fatal (like a malformed JSON payload missing
 * an 'id'), skip the retries and quarantine it immediately to save processing time.
 */

@Configuration
public class KafkaErrorHandlingConfig {
    // the no. of times the consumer should attempt to re-process failing message before giving up.
    private static final long MAX_RETRIES = 3L;

    // Defining the back-off period. If a database lock caused the failure, retrying immediately in 1 ms
    // will just hit the same lock so pausing the consumer thread for 2s to let transient issues resolve.
    private static final long RETRY_INTERVAL_MS = 2_000L;

    // Spring Kafka automatically looks for a bean of type 'DefaultErrorHandler'.
    // By defining this method and annotating it with @Bean, we override Spring's
    // default infinite-retry behavior with our custom logic. We inject the
    // KafkaTemplate so the error handler has the ability to publish messages.
    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafkaTemplate){

        // The DeadLetterPublishingRecoverer takes the failed payload and publishes it to a
        // new topic. By default, it appends ".DLT" to the original topic name. For eg. if a
        // message fails on 'finflow.events.billing', it gets  published to
        // 'finflow.events.billing.DLT'. It also adds headers detailing the exception stack trace.
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate);

        // instantiating the FixedBackoff object using our constants(wait, try)
        FixedBackOff backOff = new FixedBackOff(RETRY_INTERVAL_MS, MAX_RETRIES);

        // combining the backoff policy and the recoverer into the final DefaultErrorHandler object.
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);

        // The Short-Circuit Optimization for Fatal Exceptions-
        // If our EventEnvelopeParser tries to read the Kafka message and realizes it
        // is missing the 'id' header required for our exactly-once idempotency logic,
        // it throws an IllegalStateException.
        // Waiting 6 seconds (3x2s) won't make the 'id' appear in the JSON.
        // This line tells the handler: "If you see this specific exception, skip the
        // retries entirely and fire it straight into the DLT."
        handler.addNotRetryableExceptions(IllegalStateException.class);

        // Return the Bean - Hand the fully configured policy back to Spring Boot to attach to all
        // @KafkaListener methods in this microservice.
        return handler;
    }
}
