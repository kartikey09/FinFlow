package io.finflow.saga.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.finflow.saga.model.SagaInstance;
import io.finflow.saga.model.SagaType;
import io.finflow.saga.model.Vendor;
import io.finflow.saga.orchestration.SagaOrchestrationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @WebMvcTest boots ONLY the web layer — this controller and its dependencies
 * are wired, but no DB, no Kafka, no Spring Boot autoconfiguration for anything
 * outside the MVC slice. The orchestration service is provided as a mock via
 * @TestConfiguration. Fast (sub-second) and hermetic.
 */
@WebMvcTest(RebalanceController.class)
class RebalanceControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired SagaOrchestrationService orchestration;

    @Test
    void postRebalance_returns202AndSagaShape() throws Exception {
        SagaInstance saga = new SagaInstance(UUID.randomUUID(), SagaType.REBALANCE, "corr-abc", Vendor.AWS);
        when(orchestration.startRebalance(eq("corr-abc"), any())).thenReturn(saga);

        mockMvc.perform(post("/sagas/rebalance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RebalanceRequest("corr-abc", Vendor.AWS))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.correlationId").value("corr-abc"))
                .andExpect(jsonPath("$.currentState").value("STARTED"));
    }

    @Test
    void postRebalance_withBlankCorrelation_returns400() throws Exception {
        mockMvc.perform(post("/sagas/rebalance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correlationId\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getById_notFound_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(orchestration.findById(id)).thenReturn(Optional.empty());

        mockMvc.perform(get("/sagas/{id}", id))
                .andExpect(status().isNotFound());
    }

    @Test
    void getById_found_returnsBody() throws Exception {
        SagaInstance saga = new SagaInstance(UUID.randomUUID(), SagaType.REBALANCE, "corr-xyz", Vendor.AWS);
        when(orchestration.findById(saga.getId())).thenReturn(Optional.of(saga));

        mockMvc.perform(get("/sagas/{id}", saga.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(saga.getId().toString()))
                .andExpect(jsonPath("$.correlationId").value("corr-xyz"));
    }

    @TestConfiguration
    static class MockOrchestrationConfig {
        @Bean SagaOrchestrationService orchestrationService() {
            return mock(SagaOrchestrationService.class);
        }
    }
}
