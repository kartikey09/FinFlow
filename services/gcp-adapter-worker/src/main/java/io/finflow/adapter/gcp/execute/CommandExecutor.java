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
import java.util.concurrent.ExecutionException;

/**
 * GCP mirror of aws-adapter-worker's CommandExecutor. Same dedup / call /
 * record / emit discipline; Day 21 blocks on the CompletableFuture the GCP
 * Chaos client now returns and unwraps the ExecutionException.
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
            chaosClient.postCommitmentAction(commitmentId, action).get();
            recordAndEmitSuccess(command);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            recordAndEmitFailure(command, describe(cause));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            recordAndEmitFailure(command, "Interrupted while calling Chaos API");
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

    private static String describe(Throwable e) {
        String message = e.getMessage();
        if (message == null) message = e.getClass().getSimpleName();
        if (message.length() > 400) message = message.substring(0, 400) + "...";
        return message;
    }
}
