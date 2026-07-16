package io.finflow.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * The Java blueprint for the Transactional Outbox database table.
 *
 * What this does:
 * This class maps directly to the `outbox_event` table in PostgreSQL. Every time
 * your application creates one of these objects and saves it, Hibernate translates
 * it into a new database row.
 *
 * The clever design choice:
 * The `payload` (the actual event data) is stored as a simple text String instead
 * of a specific Java object (like a BillingEvent). This keeps this outbox library
 * completely generic. Any microservice can convert its unique data into a JSON
 * string and save it here, and this library never needs to know or care what the
 * data actually looks like.
 *
 * <h2>Day 24: traceParent</h2>
 *
 * The seventh field carries the W3C traceparent that was active when the event was
 * appended. It is the ONLY way a trace survives the outbox: the app never talks to
 * Kafka, so it cannot inject a Kafka header — Debezium does that, and Debezium has
 * no access to the app's tracing context. Persisting the context in the row lets
 * Debezium's EventRouter copy it into a Kafka header on the way out.
 *
 * <p>Declared LAST on purpose. Lombok's {@code @AllArgsConstructor} follows field
 * declaration order, so appending here keeps every existing argument in the same
 * position — the only change at the single call site (OutboxAppender) is one extra
 * trailing argument.
 */
@Entity
@Table(name = "outbox_event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // Generates the protected constructor for JPA
@AllArgsConstructor // Generates the public constructor for your code
public class OutboxEvent {
    @Id
    private UUID id;

    @Column(name = "aggregate_type", nullable = false)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private String aggregateId;

    @Column(nullable = false)
    private String type;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    /**
     * Day 24. W3C traceparent ("00-{traceId}-{spanId}-{flags}") captured from the
     * span that was current when append() ran. NULLABLE — an event appended with no
     * active span is still valid; it simply starts a fresh trace downstream.
     */
    @Column(name = "trace_parent", length = 64)
    private String traceParent;
}
