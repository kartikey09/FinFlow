package io.finflow.adapter.aws.execute;

import io.finflow.adapter.aws.chaos.AwsChaosClient;
import io.finflow.adapter.aws.chaos.StepEndpointMap;
import io.finflow.adapter.aws.consumer.SagaCommand;
import io.finflow.adapter.aws.dedup.ProcessedCommand;
import io.finflow.adapter.aws.dedup.ProcessedCommandRepository;
import io.finflow.adapter.aws.emit.SagaEventEmitter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.concurrent.ExecutionException;

/**
 * Day 18's heart. For one incoming {@link SagaCommand}:
 *
 * <pre>
 *   1. Look up idempotency_key in processed_command.
 *      HIT  → skip the Chaos call, but STILL re-publish the cached result event.
 *      MISS → fall through.
 *
 *   2. Call the Chaos API endpoint mapped from step.
 *      Wrapped in Resilience4j: retry + circuit breaker + time limiter + bulkhead
 *      (see AwsChaosClient). RestClient's read-timeout is a backstop below the
 *      7s TimeLimiter.
 *
 *   3. Record the outcome in processed_command (side effect happens exactly once).
 *
 *   4. Emit the result event to saga.events via the outbox (SagaEventEmitter).
 *
 *   Steps 3 and 4 run in ONE @Transactional — the appender is
 *   @Transactional(MANDATORY). So the side-effect record and the result event
 *   commit or fail together.
 * </pre>
 *
 * <p><b>Day 21 change:</b> the Chaos client now returns
 * {@code CompletableFuture<Void>} (so {@code @TimeLimiter} can cancel it at 7s).
 * This method blocks on the future with {@code .get()} and unwraps the
 * ExecutionException, keeping the same dedup+emit flow it always had. The
 * blocking pattern is deliberate: this executor runs on a Kafka listener
 * thread inside a {@code @Transactional}; we don't want the transaction's
 * lifetime to depend on when a background thread finishes.
 *
 * <p><b>Why re-publish on a HIT?</b> Kafka redelivery of the command usually
 * means the previous attempt's ack failed. The orchestrator may not have seen
 * the result event yet — a hit here without a re-publish would silently lose
 * the outcome. Re-publishing is cheap (an outbox insert) and idempotent on
 * the orchestrator side because Day 16's degenerate-cases branch handles
 * repeated events gracefully.
 */
@Service
public class CommandExecutor {

    private static final Logger log = LoggerFactory.getLogger(CommandExecutor.class);

    private final ProcessedCommandRepository processedRepository;
    private final AwsChaosClient chaosClient;
    private final StepEndpointMap endpointMap;
    private final SagaEventEmitter eventEmitter;

    public CommandExecutor(ProcessedCommandRepository processedRepository,
                           AwsChaosClient chaosClient,
                           StepEndpointMap endpointMap,
                           SagaEventEmitter eventEmitter) {
        this.processedRepository = processedRepository;
        this.chaosClient = chaosClient;
        this.endpointMap = endpointMap;
        this.eventEmitter = eventEmitter;
    }

    @Transactional
    public void execute(SagaCommand command) {
        if (command == null || command.idempotencyKey() == null) {
            log.warn("Dropping malformed command (missing idempotencyKey)");
            return;
        }

        Optional<ProcessedCommand> cached = processedRepository.findById(command.idempotencyKey());
        if (cached.isPresent()) {
            ProcessedCommand prior = cached.get();
            log.info("Idempotent replay for saga={} step={} direction={} — re-emitting cached {} result",
                    command.sagaId(), command.step(), command.direction(), prior.getOutcome());
            eventEmitter.publish(command.sagaId(), command.step(), prior.isSuccess(), prior.getReason());
            return;
        }

        String action = endpointMap.actionFor(command.step());
        if (action == null) {
            recordAndEmitFailure(command, "Unknown step: " + command.step());
            return;
        }

        try {
            // The commitmentId here is a placeholder until Day 20 wires the real
            // one from the SagaInstance's payload. For the demo, it lets the
            // chaos-api's ChaosInterceptor decide the fate of the call.
            String commitmentId = "saga-" + command.sagaId();
            // Day 21: block on the CompletableFuture. Resilience4j (Retry + CB +
            // TimeLimiter + Bulkhead) is applied inside the future's execution;
            // if it completes exceptionally, .get() throws ExecutionException
            // wrapping the underlying HTTP or timeout cause.
            chaosClient.postCommitmentAction(commitmentId, action).get();
            recordAndEmitSuccess(command);
        } catch (ExecutionException e) {
            // Unwrap the resilience-layer exception to get the underlying HTTP or timeout.
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            recordAndEmitFailure(command, describe(cause));
        } catch (InterruptedException e) {
            // Preserve the interrupt flag for a well-behaved shutdown.
            Thread.currentThread().interrupt();
            recordAndEmitFailure(command, "Interrupted while calling Chaos API");
        } catch (Exception e) {
            // Resilience4j has already retried; if we're here the failure is
            // final for this command. Report it as a business FAILURE — the
            // orchestrator's transition switch will start compensation.
            recordAndEmitFailure(command, describe(e));
        }
    }

    private void recordAndEmitSuccess(SagaCommand command) {
        processedRepository.save(new ProcessedCommand(
                command.idempotencyKey(), command.sagaId(),
                command.step(), command.direction(),
                "SUCCESS", null));
        eventEmitter.publish(command.sagaId(), command.step(), true, null);
        log.info("Executed OK: saga={} step={} direction={}",
                command.sagaId(), command.step(), command.direction());
    }

    private void recordAndEmitFailure(SagaCommand command, String reason) {
        processedRepository.save(new ProcessedCommand(
                command.idempotencyKey(), command.sagaId(),
                command.step(), command.direction(),
                "FAILURE", reason));
        eventEmitter.publish(command.sagaId(), command.step(), false, reason);
        log.info("Executed FAILED: saga={} step={} direction={} reason={}",
                command.sagaId(), command.step(), command.direction(), reason);
    }

    private static String describe(Throwable e) {
        // Chop long stack-trace-ish messages; the reason field is capped at 512
        // and the orchestrator only needs the headline.
        String message = e.getMessage();
        if (message == null) message = e.getClass().getSimpleName();
        if (message.length() > 400) message = message.substring(0, 400) + "...";
        return message;
    }
}
