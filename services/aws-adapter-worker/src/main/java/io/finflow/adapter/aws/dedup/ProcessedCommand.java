package io.finflow.adapter.aws.dedup;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One row per command the adapter has already applied its side effect for.
 * The PK is {@code idempotencyKey} — deterministic from
 * (sagaId + step + direction) — so a Kafka redelivery finds itself before
 * making the Chaos API call twice.
 *
 * <p>Also stores the RESULT (SUCCESS or FAILURE + optional reason). This is
 * what lets a redelivery re-publish the SAME result event without re-calling
 * the endpoint: side effect once, event as many times as Kafka needs.
 */
@Entity
@Table(name = "processed_command")
public class ProcessedCommand {

    @Id
    @Column(name = "idempotency_key", length = 255)
    private String idempotencyKey;

    @Column(name = "saga_id", nullable = false)
    private UUID sagaId;

    @Column(name = "step", length = 64, nullable = false)
    private String step;

    @Column(name = "direction", length = 8, nullable = false)
    private String direction;

    @Column(name = "outcome", length = 16, nullable = false)
    private String outcome;      // 'SUCCESS' | 'FAILURE'

    @Column(name = "reason", length = 512)
    private String reason;

    @Column(name = "processed_at", nullable = false)
    private OffsetDateTime processedAt = OffsetDateTime.now();

    protected ProcessedCommand() { /* for JPA */ }

    public ProcessedCommand(String idempotencyKey, UUID sagaId, String step, String direction,
                            String outcome, String reason) {
        this.idempotencyKey = idempotencyKey;
        this.sagaId = sagaId;
        this.step = step;
        this.direction = direction;
        this.outcome = outcome;
        this.reason = reason;
    }

    public String getIdempotencyKey() { return idempotencyKey; }
    public UUID   getSagaId()         { return sagaId; }
    public String getStep()           { return step; }
    public String getDirection()      { return direction; }
    public String getOutcome()        { return outcome; }
    public String getReason()         { return reason; }
    public OffsetDateTime getProcessedAt() { return processedAt; }

    public boolean isSuccess() { return "SUCCESS".equals(outcome); }
}
