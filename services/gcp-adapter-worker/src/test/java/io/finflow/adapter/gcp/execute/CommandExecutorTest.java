package io.finflow.adapter.gcp.execute;

import io.finflow.adapter.gcp.chaos.GcpChaosClient;
import io.finflow.adapter.gcp.chaos.StepEndpointMap;
import io.finflow.adapter.gcp.consumer.SagaCommand;
import io.finflow.adapter.gcp.dedup.ProcessedCommand;
import io.finflow.adapter.gcp.dedup.ProcessedCommandRepository;
import io.finflow.adapter.gcp.emit.SagaEventEmitter;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.client.HttpServerErrorException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Same six behaviors as aws-adapter-worker's CommandExecutorTest — the executor
 * is the mirror, so its guarantees should be the mirror too:
 *   1. First-time DO: chaos call, dedup SUCCESS, success event.
 *   2. Redelivery HIT (success): no chaos, cached success event re-emitted.
 *   3. Redelivery HIT (failure): no chaos, cached failure event + reason.
 *   4. Chaos failure: dedup FAILURE, failure event + reason, no throw.
 *   5. Unknown step: reported as FAILURE.
 *   6. Malformed command: dropped silently.
 */
class CommandExecutorTest {

    private final ProcessedCommandRepository processedRepo = mock(ProcessedCommandRepository.class);
    private final GcpChaosClient chaosClient = mock(GcpChaosClient.class);
    private final StepEndpointMap endpointMap = new StepEndpointMap();      // pure, use real
    private final SagaEventEmitter emitter = mock(SagaEventEmitter.class);

    private final CommandExecutor executor =
            new CommandExecutor(processedRepo, chaosClient, endpointMap, emitter);

    @Test
    void firstTimeDoCommand_callsChaosSavesSuccessEmitsSuccessEvent() {
        UUID sagaId = UUID.randomUUID();
        SagaCommand cmd = new SagaCommand(sagaId, "ACQUIRE_LOCK", "DO",
                sagaId + ":ACQUIRE_LOCK:DO");
        when(processedRepo.findById(cmd.idempotencyKey())).thenReturn(Optional.empty());

        executor.execute(cmd);

        verify(chaosClient).postCommitmentAction(anyString(), eq("lock"));

        ArgumentCaptor<ProcessedCommand> saved = ArgumentCaptor.forClass(ProcessedCommand.class);
        verify(processedRepo).save(saved.capture());
        assertThat(saved.getValue().getOutcome()).isEqualTo("SUCCESS");
        assertThat(saved.getValue().getReason()).isNull();

        verify(emitter).publish(eq(sagaId), eq("ACQUIRE_LOCK"), eq(true), eq(null));
    }

    @Test
    void redeliveredCommand_reEmitsCachedResult_withoutCallingChaos() {
        UUID sagaId = UUID.randomUUID();
        SagaCommand cmd = new SagaCommand(sagaId, "ACQUIRE_LOCK", "DO",
                sagaId + ":ACQUIRE_LOCK:DO");
        ProcessedCommand cached = new ProcessedCommand(
                cmd.idempotencyKey(), sagaId, "ACQUIRE_LOCK", "DO",
                "SUCCESS", null);
        when(processedRepo.findById(cmd.idempotencyKey())).thenReturn(Optional.of(cached));

        executor.execute(cmd);

        verify(chaosClient, never()).postCommitmentAction(anyString(), anyString());
        verify(processedRepo, never()).save(any());
        verify(emitter).publish(eq(sagaId), eq("ACQUIRE_LOCK"), eq(true), eq(null));
    }

    @Test
    void redeliveredFailure_reEmitsFailureEventWithSameReason() {
        UUID sagaId = UUID.randomUUID();
        SagaCommand cmd = new SagaCommand(sagaId, "RESERVE_TARGET", "DO",
                sagaId + ":RESERVE_TARGET:DO");
        ProcessedCommand cached = new ProcessedCommand(
                cmd.idempotencyKey(), sagaId, "RESERVE_TARGET", "DO",
                "FAILURE", "Chaos 503");
        when(processedRepo.findById(cmd.idempotencyKey())).thenReturn(Optional.of(cached));

        executor.execute(cmd);

        verify(chaosClient, never()).postCommitmentAction(anyString(), anyString());
        verify(emitter).publish(eq(sagaId), eq("RESERVE_TARGET"), eq(false), eq("Chaos 503"));
    }

    @Test
    void chaosApiFailure_recordsFailureEmitsFailureEvent_doesNotThrow() {
        UUID sagaId = UUID.randomUUID();
        SagaCommand cmd = new SagaCommand(sagaId, "RESERVE_TARGET", "DO",
                sagaId + ":RESERVE_TARGET:DO");
        when(processedRepo.findById(cmd.idempotencyKey())).thenReturn(Optional.empty());
        doThrow(HttpServerErrorException.create(
                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                "503 Service Unavailable",
                new org.springframework.http.HttpHeaders(),
                new byte[0], null))
                .when(chaosClient).postCommitmentAction(anyString(), eq("reserve"));

        executor.execute(cmd);

        ArgumentCaptor<ProcessedCommand> saved = ArgumentCaptor.forClass(ProcessedCommand.class);
        verify(processedRepo).save(saved.capture());
        assertThat(saved.getValue().getOutcome()).isEqualTo("FAILURE");
        assertThat(saved.getValue().getReason()).contains("503");

        verify(emitter).publish(eq(sagaId), eq("RESERVE_TARGET"), eq(false), anyString());
    }

    @Test
    void unknownStep_isReportedAsFailure_withoutCallingChaos() {
        UUID sagaId = UUID.randomUUID();
        SagaCommand cmd = new SagaCommand(sagaId, "UNKNOWN_STEP", "DO",
                sagaId + ":UNKNOWN_STEP:DO");
        when(processedRepo.findById(cmd.idempotencyKey())).thenReturn(Optional.empty());

        executor.execute(cmd);

        verify(chaosClient, never()).postCommitmentAction(anyString(), anyString());
        verify(emitter, times(1)).publish(eq(sagaId), eq("UNKNOWN_STEP"), eq(false), anyString());
    }

    @Test
    void malformedCommand_missingIdempotencyKey_isDroppedSilently() {
        SagaCommand cmd = new SagaCommand(UUID.randomUUID(), "ACQUIRE_LOCK", "DO", null);

        executor.execute(cmd);

        verify(chaosClient, never()).postCommitmentAction(anyString(), anyString());
        verify(processedRepo, never()).save(any());
        verify(emitter, never()).publish(any(), anyString(), anyBoolean(), any());
    }
}
