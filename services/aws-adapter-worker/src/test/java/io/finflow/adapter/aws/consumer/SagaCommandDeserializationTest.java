package io.finflow.adapter.aws.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SagaCommandDeserializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void fullPayload_deserializesCorrectly() throws Exception {
        UUID sagaId = UUID.randomUUID();
        String json = """
                {
                  "sagaId": "%s",
                  "step": "ACQUIRE_LOCK",
                  "direction": "DO",
                  "idempotencyKey": "some-key-123"
                }
                """.formatted(sagaId);

        SagaCommand command = objectMapper.readValue(json, SagaCommand.class);

        assertThat(command.sagaId()).isEqualTo(sagaId);
        assertThat(command.step()).isEqualTo("ACQUIRE_LOCK");
        assertThat(command.direction()).isEqualTo("DO");
        assertThat(command.idempotencyKey()).isEqualTo("some-key-123");
        assertThat(command.isUndo()).isFalse();
    }

    @Test
    void undoDirection_isUndoReturnsTrue() throws Exception {
        String json = """
                {
                  "sagaId": "%s",
                  "step": "RESERVE_TARGET",
                  "direction": "UNDO",
                  "idempotencyKey": "undo-key-456"
                }
                """.formatted(UUID.randomUUID());

        SagaCommand command = objectMapper.readValue(json, SagaCommand.class);

        assertThat(command.isUndo()).isTrue();
    }

    @Test
    void extraFields_areIgnored() throws Exception {
        String json = """
                {
                  "sagaId": "%s",
                  "step": "UPDATE_LEDGER",
                  "direction": "DO",
                  "idempotencyKey": "key-789",
                  "unknownFutureField": "should-be-ignored"
                }
                """.formatted(UUID.randomUUID());

        SagaCommand command = objectMapper.readValue(json, SagaCommand.class);

        assertThat(command.step()).isEqualTo("UPDATE_LEDGER");
    }
}
