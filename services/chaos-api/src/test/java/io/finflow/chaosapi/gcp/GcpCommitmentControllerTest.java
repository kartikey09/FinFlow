package io.finflow.chaosapi.gcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import org.springframework.boot.test.mock.mockito.MockBean;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;
import io.finflow.chaosapi.chaos.ChaosDecider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The CUD endpoint has no stored data and no dependencies, so a bare
 * @WebMvcTest(GcpCommitmentController.class) is all it needs.
 */
@WebMvcTest(io.finflow.chaosapi.gcp.GcpCommitmentController.class)
class GcpCommitmentControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean
    ChaosDecider chaosDecider;

    @BeforeEach
    void disableChaosForTests() {
        // Tell the fake dice-roller to always let traffic through
        Mockito.when(chaosDecider.decide()).thenReturn(ChaosDecider.Outcome.PASS);
    }

    @Autowired ObjectMapper objectMapper;

    private static final String BODY = """
        {"projectId":"finflow-ingestion","region":"us-central1",
         "plan":"TWELVE_MONTH","resourceType":"VCPU","amount":32}
        """;

    @Test
    void createCommitment_returnsResourceNameAndActiveStatus() throws Exception {
        mockMvc.perform(post("/gcp/billing/commitments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.commitmentName").value(startsWith(
                        "projects/finflow-ingestion/regions/us-central1/commitments/cud-")))
                .andExpect(jsonPath("$.commitmentName").value(matchesPattern(
                        "projects/finflow-ingestion/regions/us-central1/commitments/cud-[0-9a-f]{8}")))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void createCommitment_eachCallReturnsADistinctName() throws Exception {
        assertThat(doCreate()).isNotEqualTo(doCreate());
    }

    private String doCreate() throws Exception {
        MvcResult result = mockMvc.perform(post("/gcp/billing/commitments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("commitmentName").asText();
    }
}
