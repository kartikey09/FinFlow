package io.finflow.adapter.gcp.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The wire shape is identical to what the AWS adapter parses — the orchestrator
 * emits the same JSON on both saga.commands.aws and saga.commands.gcp. Pins
 * that here so a future orchestrator change gets caught by BOTH adapters, not
 * just one.
 */
class SagaCommandDeserializationTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void deserializesTheOrchestratorPayload() throws Exception {
        UUID id = UUID.randomUUID();
        String json = """
                {
                  "sagaId": "%s",
                  "step": "ACQUIRE_LOCK",
                  "direction": "DO",
                  "idempotencyKey": "%s:ACQUIRE_LOCK:DO"
                }""".formatted(id, id);

        SagaCommand cmd = mapper.readValue(json, SagaCommand.class);

        assertThat(cmd.sagaId()).isEqualTo(id);
        assertThat(cmd.step()).isEqualTo("ACQUIRE_LOCK");
        assertThat(cmd.direction()).isEqualTo("DO");
        assertThat(cmd.isUndo()).isFalse();
    }

    @Test
    void undoDirectionIsRecognized() throws Exception {
        UUID id = UUID.randomUUID();
        String json = """
                {
                  "sagaId": "%s",
                  "step": "RESERVE_TARGET",
                  "direction": "UNDO",
                  "idempotencyKey": "%s:RESERVE_TARGET:UNDO"
                }""".formatted(id, id);

        SagaCommand cmd = mapper.readValue(json, SagaCommand.class);

        assertThat(cmd.isUndo()).isTrue();
    }

    @Test
    void unknownFieldsAreIgnored() throws Exception {
        UUID id = UUID.randomUUID();
        String json = """
                {
                  "sagaId": "%s",
                  "step": "ACQUIRE_LOCK",
                  "direction": "DO",
                  "idempotencyKey": "abc",
                  "futureFieldThePlanAdds": "ignored"
                }""".formatted(id);

        SagaCommand cmd = mapper.readValue(json, SagaCommand.class);   // must not throw

        assertThat(cmd.sagaId()).isEqualTo(id);
    }
}
