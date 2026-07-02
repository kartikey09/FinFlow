package io.finflow.saga.domain;

import io.finflow.saga.model.SagaInstance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository over {@link SagaInstance}.
 *
 * <p>Two facts about how the orchestrator uses this:
 * <ul>
 *   <li>The Kafka listener does {@code findById(sagaId)}. The row's
 *       {@code @Version} handles concurrent writers — no pessimistic lock
 *       needed. If two orchestrator instances race, the second
 *       {@code save()} throws {@code OptimisticLockingFailureException},
 *       Spring rolls back, and Kafka redelivers. That's exactly what we want.</li>
 *   <li>{@link #findByCorrelationId} lets callers ask "do I already have a saga
 *       for this correlation?" — useful for idempotent creation from the REST
 *       endpoint.</li>
 * </ul>
 */
public interface SagaInstanceRepository extends JpaRepository<SagaInstance, UUID> {

    Optional<SagaInstance> findByCorrelationId(String correlationId);

    /** All non-terminal sagas — used later for observability / stuck-saga alerts. */
    @Query("""
            SELECT s FROM SagaInstance s
            WHERE s.currentState NOT IN (
                io.finflow.saga.model.SagaState.COMPLETED,
                io.finflow.saga.model.SagaState.COMPENSATED,
                io.finflow.saga.model.SagaState.COMPENSATION_FAILED)
            """)
    List<SagaInstance> findAllInFlight();
}
