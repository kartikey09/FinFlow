package io.finflow.chaosapi.aws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import org.springframework.boot.test.mock.mockito.MockBean;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;
import io.finflow.chaosapi.chaos.ChaosDecider;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the AWS billing endpoint paginates and — critically — serializes
 * the real CUR field names. The field-name assertions are the whole point:
 * the Week-3 ingestor parses these exact keys.
 */
@WebMvcTest(AwsBillingController.class)
@Import(SyntheticCurData.class)
class AwsBillingControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean
    ChaosDecider chaosDecider;

    @BeforeEach
    void disableChaosForTests() {
        // Tell the fake dice-roller to always let traffic through
        Mockito.when(chaosDecider.decide()).thenReturn(ChaosDecider.Outcome.PASS);
    }
    @Autowired ObjectMapper objectMapper;

    @Test
    void firstPage_hasCurFieldNames_andANextToken() throws Exception {
        mockMvc.perform(get("/aws/cost-and-usage-report"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.billingPeriod").value("2025-11"))
                .andExpect(jsonPath("$.lineItems").isArray())
                .andExpect(jsonPath("$.lineItems[0]['identity/LineItemId']").exists())
                .andExpect(jsonPath("$.lineItems[0]['lineItem/LineItemType']").exists())
                .andExpect(jsonPath("$.lineItems[0]['lineItem/UnblendedCost']").exists())
                .andExpect(jsonPath("$.nextToken").value("5"));
    }

    @Test
    void lastPage_hasNoNextToken() throws Exception {
        mockMvc.perform(get("/aws/cost-and-usage-report").param("nextToken", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lineItems.length()").value(2))
                .andExpect(jsonPath("$.nextToken").doesNotExist());
    }

    @Test
    void garbageToken_startsFromBeginning() throws Exception {
        mockMvc.perform(get("/aws/cost-and-usage-report").param("nextToken", "not-a-number"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lineItems[0]['identity/LineItemId']")
                        .value("01HEXJ7K2M8N9P3Q5R7S9T0001"));
    }

    @Test
    void fullPaginationWalk_returnsAllTwelveItemsExactlyOnce() throws Exception {
        Set<String> seen = new LinkedHashSet<>();
        String token = "";
        int pages = 0;

        while (true) {
            MvcResult result = mockMvc.perform(get("/aws/cost-and-usage-report")
                            .param("nextToken", token))
                    .andExpect(status().isOk())
                    .andReturn();

            JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
            for (JsonNode li : body.get("lineItems")) {
                seen.add(li.get("identity/LineItemId").asText());
            }
            pages++;

            if (body.hasNonNull("nextToken")) {
                token = body.get("nextToken").asText();
            } else {
                break;
            }
            if (pages > 10) break;
        }

        assertThat(pages).isEqualTo(3);
        assertThat(seen).hasSize(12);
    }
}
