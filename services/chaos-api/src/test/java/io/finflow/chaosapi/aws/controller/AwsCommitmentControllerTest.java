package io.finflow.chaosapi.aws.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import io.finflow.chaosapi.chaos.ChaosDecider;
import org.mockito.Mockito;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.junit.jupiter.api.BeforeEach;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AwsCommitmentController.class)
class AwsCommitmentControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean ChaosDecider chaosDecider;

    @BeforeEach
    void disableChaos() {
        Mockito.when(chaosDecider.decide()).thenReturn(ChaosDecider.Outcome.PASS);
    }

    private static final String BODY = """
        {"reservedInstancesOfferingId":"offer-abc","instanceCount":10}
        """;

    @Test
    void purchase_returnsReservationIdAndActiveState() throws Exception {
        mockMvc.perform(post("/aws/ec2/purchase-reserved-instances")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isOk())
                // "ri-" + first 12 chars of a UUID; those 12 chars include a hyphen,
                // so allow any char here — NOT [0-9a-f]{12}.
                .andExpect(jsonPath("$.reservationId").value(matchesPattern("ri-.{12}")))
                .andExpect(jsonPath("$.state").value("active"));
    }

    @Test
    void purchase_eachCallReturnsADistinctReservationId() throws Exception {
        assertThat(doPurchase()).isNotEqualTo(doPurchase());
    }

    private String doPurchase() throws Exception {
        MvcResult result = mockMvc.perform(post("/aws/ec2/purchase-reserved-instances")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("reservationId").asText();
    }
}
