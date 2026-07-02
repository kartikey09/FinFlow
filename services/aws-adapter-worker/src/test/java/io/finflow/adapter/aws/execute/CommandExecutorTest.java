package io.finflow.adapter.aws.execute;

import io.finflow.adapter.aws.chaos.AwsChaosClient;
import io.finflow.adapter.aws.chaos.StepEndpointMap;
import io.finflow.adapter.aws.consumer.SagaCommand;
import io.finflow.adapter.aws.dedup.ProcessedCommand;
import io.finflow.adapter.aws.dedup.ProcessedCommandRepository;
import io.finflow.adapter.aws.emit.SagaEventEmitter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommandExecutorTest {

    @Mock ProcessedCommandRepository processedRepository;
    @Mock AwsChaosClient chaosClient;
    @Mock SagaEventEmitter eventEmitter;

    private CommandExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new CommandExecutor(processedRepository, chaosClient, new StepEndpointMap(), eventEmitter);
    }

    @Test
    void successfulExecution_recordsSuccessAndEmitsSuccessEvent() {
        UUID sagaId = UUID.randomUUID();
        SagaCommand command = new SagaCommand(sagaId, "ACQUIRE_LOCK", "DO", "key-1");
        when(processedRepository.findById("key-1")).thenReturn(Optional.empty());

        executor.execute(command);

        ArgumentCaptor<ProcessedCommand> saved = ArgumentCaptor.forClass(ProcessedCommand.class);
        verify(processedRepository).save(saved.capture());
        assertThat(saved.getValue().getOutcome()).isEqualTo("SUCCESS");
        assertThat(saved.getValue().getSagaId()).isEqualTo(sagaId);
        verify(eventEmitter).publish(sagaId, "ACQUIRE_LOCK", true, null);
        verify(chaosClient).postCommitmentAction(anyString(), eq("lock"));
    }

    @Test
    void chaosApiThrows_recordsFailureAndEmitsFailureEvent() {
        UUID sagaId = UUID.randomUUID();
        SagaCommand command = new SagaCommand(sagaId, "VERIFY_COMMITMENT", "DO", "key-2");
        when(processedRepository.findById("key-2")).thenReturn(Optional.empty());
        doThrow(new RuntimeException("503 Service Unavailable"))
                .when(chaosClient).postCommitmentAction(anyString(), eq("verify"));

        executor.execute(command);

        ArgumentCaptor<ProcessedCommand> saved = ArgumentCaptor.forClass(ProcessedCommand.class);
        verify(processedRepository).save(saved.capture());
        assertThat(saved.getValue().getOutcome()).isEqualTo("FAILURE");
        assertThat(saved.getValue().getReason()).contains("503");
        verify(eventEmitter).publish(eq(sagaId), eq("VERIFY_COMMITMENT"), eq(false), anyString());
    }

    @Test
    void idempotentReplay_skipsChaosCallAndReEmitsCachedSuccessEvent() {
        UUID sagaId = UUID.randomUUID();
        SagaCommand command = new SagaCommand(sagaId, "ACQUIRE_LOCK", "DO", "key-3");
        ProcessedCommand prior = new ProcessedCommand("key-3", sagaId, "ACQUIRE_LOCK", "DO", "SUCCESS", null);
        when(processedRepository.findById("key-3")).thenReturn(Optional.of(prior));

        executor.execute(command);

        verifyNoInteractions(chaosClient);
        verify(eventEmitter).publish(sagaId, "ACQUIRE_LOCK", true, null);
        verify(processedRepository, never()).save(any());
    }

    @Test
    void idempotentReplay_reEmitsCachedFailureEvent() {
        UUID sagaId = UUID.randomUUID();
        SagaCommand command = new SagaCommand(sagaId, "RESERVE_TARGET", "DO", "key-4");
        ProcessedCommand prior = new ProcessedCommand("key-4", sagaId, "RESERVE_TARGET", "DO",
                "FAILURE", "timeout");
        when(processedRepository.findById("key-4")).thenReturn(Optional.of(prior));

        executor.execute(command);

        verifyNoInteractions(chaosClient);
        verify(eventEmitter).publish(sagaId, "RESERVE_TARGET", false, "timeout");
    }

    @Test
    void unknownStep_recordsFailureWithoutCallingChaosApi() {
        UUID sagaId = UUID.randomUUID();
        SagaCommand command = new SagaCommand(sagaId, "UNKNOWN_STEP", "DO", "key-5");
        when(processedRepository.findById("key-5")).thenReturn(Optional.empty());

        executor.execute(command);

        verifyNoInteractions(chaosClient);
        ArgumentCaptor<ProcessedCommand> saved = ArgumentCaptor.forClass(ProcessedCommand.class);
        verify(processedRepository).save(saved.capture());
        assertThat(saved.getValue().getOutcome()).isEqualTo("FAILURE");
        assertThat(saved.getValue().getReason()).contains("Unknown step");
    }

    @Test
    void nullIdempotencyKey_dropsCommandWithoutPersisting() {
        SagaCommand command = new SagaCommand(UUID.randomUUID(), "ACQUIRE_LOCK", "DO", null);

        executor.execute(command);

        verifyNoInteractions(processedRepository, chaosClient, eventEmitter);
    }
}
