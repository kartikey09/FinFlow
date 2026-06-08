package io.finflow.ingestion.aws.smoke;

/**
 * The payload round-tripped through Kafka by the Week-1 smoke test. A simple
 * record so Jackson's type headers carry it cleanly and the consumer can
 * deserialize it with trusted-packages = io.finflow.*.
 */
public record SmokeMessage(String id, String payload, String source) {
}
