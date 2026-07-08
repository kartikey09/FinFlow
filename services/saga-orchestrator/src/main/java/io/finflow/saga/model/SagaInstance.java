package io.finflow.saga.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * One saga in flight. This entity is the entire recovery story: everything the
 * orchestrator needs to resume after a crash lives here — the current state,
 * the ordered list of steps completed so far, and the correlation id that ties
 * incoming events back to this row.
 *
 * <p><b>Why {@code @Version}?</b> When Kafka redelivers an event mid-transition
 * (e.g. the orchestrator crashed just after emitting the next command but
 * before committing), two orchestrator instances could momentarily be applying
 * the same event. Optimistic locking on this column makes the second commit
 * fail cleanly (StaleObjectStateException) — Spring rolls back, Kafka
 * redelivers again, and by then the state has actually advanced. Idempotency
 * for free without a dedup table.
 *
 * <p><b>Why {@code completedSteps} as JSONB?</b> The compensation walk needs
 * to know exactly which steps ran, in order. A JSONB array on the same row
 * keeps it colocated with the state — one row read to resume, one row write to
 * transition, no join. Jackson serializes the {@link SagaStep} enum to strings
 * automatically.
 *
 * <p><b>Why plain mutable fields?</b> This is Hibernate's happy path — entities
 * can't be records (JPA needs a no-arg constructor and mutable state), and the
 * project convention already leans that way.
 */
@Entity
@Table(name = "saga_instances")
public class SagaInstance {

    @Id
    @Column(name = "id")
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 32, nullable = false)
    private SagaType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_state", length = 32, nullable = false)
    private SagaState currentState;

    /**
     * Day 20: which vendor's adapter handles this saga's commands. The emitter
     * reads it to route to {@code saga.commands.aws} vs {@code saga.commands.gcp}.
     * Nullable so pre-Day-20 rows survive the V2 migration without a backfill.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "vendor", length = 16)
    private Vendor vendor;

    /**
     * Ordered list of steps that have committed successfully. Compensation
     * reads this in reverse. Serialized as JSONB.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "completed_steps", columnDefinition = "jsonb", nullable = false)
    private List<SagaStep> completedSteps = new ArrayList<>();

    /**
     * Optional caller-provided key for tracing / idempotent creation of the
     * saga itself. Not the row PK — {@link #id} is — but the value that shows
     * up in logs and in the Kafka key.
     */
    @Column(name = "correlation_id", length = 128, nullable = false)
    private String correlationId;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    protected SagaInstance() { /* for JPA */ }

    public SagaInstance(UUID id, SagaType type, String correlationId, Vendor vendor) {
        this.id = id;
        this.type = type;
        this.correlationId = correlationId;
        this.vendor = vendor;
        this.currentState = SagaState.STARTED;
    }

    /**
     * Apply the result of a {@link io.finflow.saga.transition.SagaTransitionService}
     * evaluation to this instance. Pushing the "record" of a completed step and
     * moving to the new state are one operation — you never advance without
     * recording, and you never record without advancing.
     */
    public void applyTransition(SagaState newState, SagaStep justCompleted) {
        if (justCompleted != null) {
            this.completedSteps.add(justCompleted);
        }
        this.currentState = newState;
        this.updatedAt = OffsetDateTime.now();
    }

    /**
     * Pop the most-recently-completed step off the stack. Called by the
     * orchestrator when a compensation Undo succeeds: the
     * {@link io.finflow.saga.transition.SagaTransitionService} computes the next
     * undo from the tail of {@code completedSteps} and expects the caller to
     * physically remove the step it just unwound, so the reverse walk shrinks
     * toward COMPENSATED. Without this pop the walk never terminates.
     */
    public void popLastCompletedStep() {
        if (!completedSteps.isEmpty()) {
            completedSteps.remove(completedSteps.size() - 1);
        }
    }

    /**
     * Read-only view of completed steps. Callers that want to walk them in
     * reverse (compensation) should reverse a defensive copy.
     */
    public List<SagaStep> getCompletedSteps() {
        return Collections.unmodifiableList(completedSteps);
    }

    public UUID getId()                    { return id; }
    public SagaType getType()              { return type; }
    public Vendor getVendor()              { return vendor; }
    public SagaState getCurrentState()     { return currentState; }
    public String getCorrelationId()       { return correlationId; }
    public Long getVersion()               { return version; }
    public OffsetDateTime getCreatedAt()   { return createdAt; }
    public OffsetDateTime getUpdatedAt()   { return updatedAt; }
}
