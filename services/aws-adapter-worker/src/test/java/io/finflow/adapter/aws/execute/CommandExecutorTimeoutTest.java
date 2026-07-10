package io.finflow.adapter.aws.execute;

import io.finflow.adapter.aws.chaos.AwsChaosClient;
import io.finflow.adapter.aws.chaos.StepEndpointMap;
import io.finflow.adapter.aws.consumer.SagaCommand;
import io.finflow.adapter.aws.dedup.ProcessedCommand;
import io.finflow.adapter.aws.dedup.ProcessedCommandRepository;
import io.finflow.adapter.aws.emit.SagaEventEmitter;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Day 21 no-Docker property test: after the Chaos client switched to returning
 * {@code CompletableFuture<Void>} (so {@code @TimeLimiter} can cancel at 7s),
 * the executor must correctly handle a future that completes exceptionally
 * with a TimeoutException — recording a FAILURE, emitting StepFailed, and NOT
 * throwing (a timeout is a business FAILURE, not an infra crash).
 *
 * <p>Complements the existing Day-18 CommandExecutorTest (dedup HIT, chaos 503,
 * unknown step, malformed). Between the two, the whole exception surface of the
 * new blocking-on-future path is covered without needing Docker or Spring AOP.
 */
class CommandExecutorTimeoutTest {

    private final ProcessedCommandRepository processedRepo = mock(ProcessedCommandRepository.class);
    private final AwsChaosClient chaosClient = mock(AwsChaosClient.class);
    private final StepEndpointMap endpointMap = new StepEndpointMap();
    private final SagaEventEmitter emitter = mock(SagaEventEmitter.class);

    private final CommandExecutor executor =
            new CommandExecutor(processedRepo, chaosClient, endpointMap, emitter);

    @Test
    void timeoutFromTimeLimiter_isRecordedAsBusinessFailure_notThrown() {
        UUID sagaId = UUID.randomUUID();
        SagaCommand cmd = new SagaCommand(sagaId, "RESERVE_TARGET", "DO",
                sagaId + ":RESERVE_TARGET:DO");
        when(processedRepo.findById(cmd.idempotencyKey())).thenReturn(Optional.empty());

        // Emulate @TimeLimiter(cancel-running-future=true) firing at 7s:
        // the returned future completes exceptionally with TimeoutException.
        CompletableFuture<Void> timedOut = new CompletableFuture<>();
        timedOut.completeExceptionally(
                new TimeoutException("TimeLimiter 'aws-chaos' recorded a timeout exception"));
        when(chaosClient.postCommitmentAction(anyString(), eq("reserve"))).thenReturn(timedOut);

        executor.execute(cmd);   // must NOT throw

        ArgumentCaptor<ProcessedCommand> saved = ArgumentCaptor.forClass(ProcessedCommand.class);
        verify(processedRepo).save(saved.capture());
        assertThat(saved.getValue().getOutcome()).isEqualTo("FAILURE");
        assertThat(saved.getValue().getReason()).containsIgnoringCase("timeout");

        verify(emitter).publish(eq(sagaId), eq("RESERVE_TARGET"), eq(false), anyString());
    }

    @Test
    void completedFuture_isRecordedAsSuccess() {
        UUID sagaId = UUID.randomUUID();
        SagaCommand cmd = new SagaCommand(sagaId, "ACQUIRE_LOCK", "DO",
                sagaId + ":ACQUIRE_LOCK:DO");
        when(processedRepo.findById(cmd.idempotencyKey())).thenReturn(Optional.empty());
        when(chaosClient.postCommitmentAction(anyString(), eq("lock")))
                .thenReturn(CompletableFuture.completedFuture(null));

        executor.execute(cmd);

        ArgumentCaptor<ProcessedCommand> saved = ArgumentCaptor.forClass(ProcessedCommand.class);
        verify(processedRepo).save(saved.capture());
        assertThat(saved.getValue().getOutcome()).isEqualTo("SUCCESS");
        verify(emitter).publish(eq(sagaId), eq("ACQUIRE_LOCK"), eq(true), eq(null));
    }
}
