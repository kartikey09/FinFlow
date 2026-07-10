package io.finflow.saga.metrics;

import io.finflow.saga.domain.SagaInstanceRepository;
import io.finflow.saga.model.SagaInstance;
import io.finflow.saga.model.SagaState;
import io.finflow.saga.model.SagaStep;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Day 22: saga observability metrics.
 *
 * <h2>finflow.saga.active — Gauge, tagged by state</h2>
 *
 * <p>One gauge per non-terminal {@link SagaState}. The value is recomputed on
 * each Prometheus scrape by counting {@code findAllInFlight()} grouped by
 * current state. Terminal states (COMPLETED / COMPENSATED / COMPENSATION_FAILED)
 * are intentionally NOT gauged — an ever-growing "COMPLETED" gauge is noise; the
 * events.published / consumed counters already capture throughput. What the
 * dashboard cares about is "how many sagas are stuck in COMPENSATING right now",
 * which this answers directly.
 *
 * <p>Registering one gauge per state (rather than a single tagged gauge whose
 * tag value changes) is the Micrometer-correct pattern: a gauge observes ONE
 * numeric source, and tag values must be bounded. The non-terminal state set is
 * small and fixed, so we pre-register a gauge for each at startup.
 *
 * <h2>finflow.saga.step.duration — Timer (histogram), tagged by step</h2>
 *
 * <p>Recorded by the orchestration service each time a step completes: the
 * elapsed wall-clock time between the saga row's previous {@code updated_at}
 * (when the command for this step was emitted) and now (when its result event
 * arrived). This measures the round-trip: orchestrator → Kafka → adapter →
 * Chaos API → adapter → Kafka → orchestrator. Tagged by step name so the
 * dashboard can show which step is slow (usually the one the Chaos API is
 * hanging on).
 */
@Component
public class SagaMetrics {

    private final MeterRegistry meterRegistry;
    private final SagaInstanceRepository sagaRepository;

    /** One live counter per non-terminal state, updated on scrape. */
    private final Map<SagaState, AtomicInteger> activeByState = new EnumMap<>(SagaState.class);

    /** Cached per-step timers so we don't rebuild the meter each event. */
    private final ConcurrentHashMap<SagaStep, Timer> stepTimers = new ConcurrentHashMap<>();

    public SagaMetrics(MeterRegistry meterRegistry, SagaInstanceRepository sagaRepository) {
        this.meterRegistry = meterRegistry;
        this.sagaRepository = sagaRepository;
    }

    @PostConstruct
    void registerGauges() {
        for (SagaState state : SagaState.values()) {
            if (state.isTerminal()) {
                continue;   // don't gauge terminal states
            }
            AtomicInteger holder = new AtomicInteger(0);
            activeByState.put(state, holder);
            Gauge.builder("finflow.saga.active", holder, AtomicInteger::get)
                    .description("Sagas currently in a non-terminal state")
                    .tag("state", state.name())
                    .register(meterRegistry);
        }
        // Bind a single refresh to each scrape by using a Gauge whose supplier
        // refreshes the whole snapshot. We attach the refresh to a lightweight
        // "meta" gauge that recomputes all state counts atomically, so the
        // per-state gauges above always read a consistent snapshot.
        Gauge.builder("finflow.saga.active.refresh", this, SagaMetrics::refreshAndReturnTotal)
                .description("Internal: recomputes per-state active counts on scrape")
                .register(meterRegistry);
    }

    /**
     * Recompute the per-state active counts. Called on scrape via the refresh
     * gauge; also returns the total so the refresh gauge has a numeric value.
     */
    private double refreshAndReturnTotal() {
        // Zero everything first so a state that dropped to 0 is reflected.
        for (AtomicInteger holder : activeByState.values()) {
            holder.set(0);
        }
        List<SagaInstance> inFlight = sagaRepository.findAllInFlight();
        for (SagaInstance saga : inFlight) {
            AtomicInteger holder = activeByState.get(saga.getCurrentState());
            if (holder != null) {
                holder.incrementAndGet();
            }
        }
        return inFlight.size();
    }

    /**
     * Record how long a saga step took (command-emit to result-event).
     * Called from the orchestration service when a step's result is applied.
     */
    public void recordStepDuration(SagaStep step, Duration elapsed) {
        if (step == null || elapsed == null || elapsed.isNegative()) {
            return;
        }
        stepTimers.computeIfAbsent(step, s ->
                Timer.builder("finflow.saga.step.duration")
                        .description("Round-trip duration of a saga step (emit to result event)")
                        .tag("step", s.name())
                        .publishPercentileHistogram()
                        .register(meterRegistry)
        ).record(elapsed);
    }
}
