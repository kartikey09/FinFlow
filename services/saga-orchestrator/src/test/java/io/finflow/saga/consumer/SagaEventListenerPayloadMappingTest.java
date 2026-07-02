package io.finflow.saga.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.finflow.saga.model.SagaStep;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The listener converts SagaEventPayload -> SagaEvent based on the success
 * flag. This test just proves the JSON contract holds: a well-formed payload
 * on the wire deserializes to the record shape the orchestrator expects.
 * The full listener is exercised end-to-end by scripts/inject-saga-event.sh
 * during Day 17's live demo.
 */
class SagaEventListenerPayloadMappingTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void successfulEventJsonMapsToRecord() throws Exception {
        UUID id = UUID.randomUUID();
        String json = """
                {
                  "sagaId": "%s",
                  "step": "ACQUIRE_LOCK",
                  "success": true
                }
                """.formatted(id);

        SagaEventPayload p = mapper.readValue(json, SagaEventPayload.class);

        assertThat(p.sagaId()).isEqualTo(id);
        assertThat(p.step()).isEqualTo(SagaStep.ACQUIRE_LOCK);
        assertThat(p.success()).isTrue();
        assertThat(p.reason()).isNull();
    }

    @Test
    void failedEventCarriesReason() throws Exception {
        UUID id = UUID.randomUUID();
        String json = """
                {
                  "sagaId": "%s",
                  "step": "RESERVE_TARGET",
                  "success": false,
                  "reason": "Chaos 503"
                }
                """.formatted(id);

        SagaEventPayload p = mapper.readValue(json, SagaEventPayload.class);

        assertThat(p.success()).isFalse();
        assertThat(p.reason()).isEqualTo("Chaos 503");
    }

    @Test
    void unknownFieldsAreIgnored() throws Exception {
        UUID id = UUID.randomUUID();
        String json = """
                {
                  "sagaId": "%s",
                  "step": "ACQUIRE_LOCK",
                  "success": true,
                  "someAdapterAddition": {"whatever": 1}
                }
                """.formatted(id);

        SagaEventPayload p = mapper.readValue(json, SagaEventPayload.class);   // must not throw

        assertThat(p.sagaId()).isEqualTo(id);
    }
}
