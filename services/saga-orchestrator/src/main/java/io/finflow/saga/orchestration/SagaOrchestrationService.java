package io.finflow.saga.orchestration;

import io.finflow.saga.command.SagaCommand;
import io.finflow.saga.domain.SagaInstanceRepository;
import io.finflow.saga.event.SagaEvent;
import io.finflow.saga.metrics.SagaMetrics;
import io.finflow.saga.model.SagaInstance;
import io.finflow.saga.model.SagaState;
import io.finflow.saga.model.SagaStep;
import io.finflow.saga.model.SagaType;
import io.finflow.saga.model.Vendor;
import io.finflow.saga.transition.SagaTransitionService;
import io.finflow.saga.transition.TransitionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The transactional bridge. Everything Day 16's pure {@code SagaTransitionService}
 * needed as context (persistence, Kafka wake-up, outbox emission) lives here.
 * The state machine stays pure and unit-testable; this class is the ONE place
 * that touches the database and the outbox in a coordinated transaction.
 *
 * <p>Two entry points:
 * <ol>
 *   <li>{@link #startRebalance(String, Vendor)} — creates a fresh {@link SagaInstance}
 *       in {@code STARTED} and emits the first command ({@code Do(ACQUIRE_LOCK)}).
 *       Called from the REST endpoint.</li>
 *   <li>{@link #handleEvent(SagaEvent)} — loads the saga, runs {@code evaluate},
 *       applies the result to the row, emits any new commands. Called from the
 *       Kafka listener on {@code saga.events}.</li>
 * </ol>
 *
 * <p>Both entry points are {@code @Transactional}. Both include the
 * outbox append INSIDE that transaction. That's the entire correctness
 * argument for the saga:
 * <ul>
 *   <li>{@code SagaInstance.currentState} is the ground truth.</li>
 *   <li>Kafka + outbox events are guaranteed-consistent with it because they
 *       commit together.</li>
 *   <li>A crash mid-transaction rolls back both; Kafka redelivers the input
 *       event; the pure {@code evaluate} function reruns; the same output.</li>
 *   <li>A crash between commit and Kafka ack means redelivery happens too, but
 *       now the state HAS advanced, so {@code evaluate}'s degenerate-case
 *       branches make it a no-op (verified by Day 16's tests).</li>
 * </ul>
 *
 * <p>Day 22: emits {@code finflow.saga.step.duration} on each completed step
 * (see {@link SagaMetrics}). The {@code finflow.saga.active} gauge lives entirely
 * in SagaMetrics (scrape-driven), so it needs no hook here.
 */
@Service
public class SagaOrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(SagaOrchestrationService.class);

    private final SagaInstanceRepository sagaRepository;
    private final SagaTransitionService transitionService;
    private final SagaCommandEmitter commandEmitter;
    private final SagaMetrics sagaMetrics;

    public SagaOrchestrationService(SagaInstanceRepository sagaRepository,
                                    SagaTransitionService transitionService,
                                    SagaCommandEmitter commandEmitter,
                                    SagaMetrics sagaMetrics) {
        this.sagaRepository = sagaRepository;
        this.transitionService = transitionService;
        this.commandEmitter = commandEmitter;
        this.sagaMetrics = sagaMetrics;
    }

    /**
     * Create a new rebalance saga, or return the existing one if the correlation
     * id is already known — idempotent on the caller's key.
     *
     * <p>On a first-time correlation: create the row (state {@code STARTED}) and
     * emit the {@code Do(ACQUIRE_LOCK)} command in the same transaction. The
     * {@code evaluate} on {@code STARTED} would require a "pretend AcquireLock
     * succeeded" event that we don't have — so this method encodes the initial
     * transition directly: it's the ONE hard-coded hop into the state machine.
     */
    @Transactional
    public SagaInstance startRebalance(String correlationId, Vendor vendor) {
        Optional<SagaInstance> existing = sagaRepository.findByCorrelationId(correlationId);
        if (existing.isPresent()) {
            log.info("Rebalance idempotent: saga {} already exists for correlation {}",
                    existing.get().getId(), correlationId);
            return existing.get();
        }

        // Day 20: the vendor is stored on the row so every subsequent command
        // (emitted by handleEvent below) routes to the same adapter.
        SagaInstance saga = new SagaInstance(UUID.randomUUID(), SagaType.REBALANCE, correlationId, vendor);
        sagaRepository.save(saga);

        // Emit the first Do — the initial hop from STARTED. The subsequent hops
        // are all driven by the pure evaluate() switch on incoming events.
        commandEmitter.emitAll(saga, List.of(new SagaCommand.Do(saga.getId(), SagaStep.ACQUIRE_LOCK)));

        log.info("Started rebalance saga {} (correlation {}, vendor {})",
                saga.getId(), correlationId, vendor);
        return saga;
    }

    /**
     * Apply one incoming event to the corresponding saga.
     *
     * <p>Idempotency: an event whose payload names a step the saga has already
     * moved past will short-circuit inside {@code evaluate} (the degenerate-
     * cases branch), leaving state and completed_steps unchanged. So calling
     * this twice with the same event is safe.
     *
     * <p>Concurrency: if two orchestrator instances try to process the same
     * event, {@code @Version} throws {@code OptimisticLockingFailureException}
     * on the second commit. Spring rolls back, we log and let Kafka redeliver.
     * When it does, the winner has already advanced state, so the redelivery
     * is a no-op.
     */
    @Transactional
    public void handleEvent(SagaEvent event) {
        Optional<SagaInstance> maybeSaga = sagaRepository.findById(event.sagaId());
        if (maybeSaga.isEmpty()) {
            log.warn("Received event for unknown saga {} — dropping", event.sagaId());
            return;
        }
        SagaInstance saga = maybeSaga.get();

        SagaState stateBefore = saga.getCurrentState();
        // Day 22: capture when the command for the current step was emitted, so
        // we can measure how long the round-trip took once the result arrives.
        OffsetDateTime stepStartedAt = saga.getUpdatedAt();
        TransitionResult result = transitionService.evaluate(
                stateBefore, saga.getCompletedSteps(), event);

        if (result.nextState() == saga.getCurrentState()
                && result.commandsToEmit().isEmpty()
                && result.justCompleted() == null) {
            log.debug("No-op transition for saga {} in state {} (event {})",
                    saga.getId(), saga.getCurrentState(), event);
            return;
        }

        try {
            saga.applyTransition(result.nextState(), result.justCompleted());
            // Day 22: record the step's round-trip duration (emit -> result event).
            if (result.justCompleted() != null && stepStartedAt != null) {
                sagaMetrics.recordStepDuration(
                        result.justCompleted(),
                        Duration.between(stepStartedAt, OffsetDateTime.now()));
            }
            // Compensation reverse-walk: a successful Undo (a StepSucceeded while
            // COMPENSATING) unwound the most-recently-completed step, so pop it off
            // the stack. The transition service reads the tail of completed_steps to
            // pick the NEXT undo, and terminates at COMPENSATED once the stack empties.
            if (stateBefore == SagaState.COMPENSATING && event instanceof SagaEvent.StepSucceeded) {
                saga.popLastCompletedStep();
            }
            // Collapse the final forward hop: once UPDATE_LEDGER succeeds the saga is
            // at LEDGER_UPDATED with no further command to emit, so no adapter event
            // will ever arrive to drive LEDGER_UPDATED -> COMPLETED. The transition
            // service models this as two hops (one state = one responsibility); in the
            // live pipeline we finalize it here so the saga actually reaches COMPLETED.
            if (saga.getCurrentState() == SagaState.LEDGER_UPDATED) {
                saga.applyTransition(SagaState.COMPLETED, null);
            }
            sagaRepository.save(saga);
            // Only touch the outbox when there's actually something to emit — a
            // terminal hop (LEDGER_UPDATED, COMPLETED, COMPENSATED) carries no command.
            if (!result.commandsToEmit().isEmpty()) {
                commandEmitter.emitAll(saga, result.commandsToEmit());
            }
        } catch (OptimisticLockingFailureException raced) {
            log.info("Concurrent update on saga {}; will retry on redelivery ({})",
                    saga.getId(), raced.getMessage());
            throw raced;   // let the caller's tx roll back; Kafka will redeliver
        }

        log.info("Saga {}: {} -> {} (emitted {} command(s))",
                saga.getId(), event, result.nextState(), result.commandsToEmit().size());
    }

    /**
     * Read-side helper used by tests and by the REST endpoint's GET-by-id
     * response. Wrapped so callers can hit it without needing to depend on
     * the repository directly.
     */
    @Transactional(readOnly = true)
    public Optional<SagaInstance> findById(UUID id) {
        return sagaRepository.findById(id);
    }
}
