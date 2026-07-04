package io.finflow.adapter.gcp.execute;

import io.finflow.adapter.gcp.chaos.GcpChaosClient;
import io.finflow.adapter.gcp.chaos.StepEndpointMap;
import io.finflow.adapter.gcp.consumer.SagaCommand;
import io.finflow.adapter.gcp.dedup.ProcessedCommand;
import io.finflow.adapter.gcp.dedup.ProcessedCommandRepository;
import io.finflow.adapter.gcp.emit.SagaEventEmitter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * The heart of Day 19. Same shape as Day 18's aws-adapter-worker executor:
 *
 * <pre>
 *   1. dedup lookup on idempotency_key
 *        HIT  -> skip GCP call, RE-EMIT cached result event  (crucial! see note)
 *        MISS -> fall through
 *
 *   2. Call the GCP Chaos endpoint mapped from step
 *        (@Retry + @CircuitBreaker + @Bulkhead in GcpChaosClient;
 *         read-timeout on the RestClient realizes the TimeLimiter spec)
 *
 *   3. Record outcome in gcp_adapter.processed_command
 *
 *   4. Emit result to saga.events via the outbox (SagaEventEmitter)
 *
 *   All in ONE @Transactional — the appender is @Transactional(MANDATORY),
 *   so the dedup row and the outbox event commit together.
 * </pre>
 *
 * <p><b>The re-emit-on-HIT subtlety is unchanged from Day 18</b> and worth
 * naming again: a redelivery usually means the previous ack failed. If we
 * skipped re-emission the orchestrator might never learn the step succeeded,
 * silently orphaning the saga. Republishing the cached result is cheap
 * (one outbox insert) and Day 16's degenerate-cases branch handles the
 * duplicate on the orchestrator side.
 */
@Service
public class CommandExecutor {

    private static final Logger log = LoggerFactory.getLogger(CommandExecutor.class);

    private final ProcessedCommandRepository processedRepository;
    private final GcpChaosClient chaosClient;
    private final StepEndpointMap endpointMap;
    private final SagaEventEmitter eventEmitter;

    public CommandExecutor(ProcessedCommandRepository processedRepository,
                           GcpChaosClient chaosClient,
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
            String commitmentId = "saga-" + command.sagaId();
            chaosClient.postCommitmentAction(commitmentId, action);
            recordAndEmitSuccess(command);
        } catch (Exception e) {
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

    private static String describe(Exception e) {
        String message = e.getMessage();
        if (message == null) message = e.getClass().getSimpleName();
        if (message.length() > 400) message = message.substring(0, 400) + "...";
        return message;
    }
}
