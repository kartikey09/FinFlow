package io.finflow.chaosapi.gcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.finflow.chaosapi.gcp.controller.GcpBillingController;
import io.finflow.chaosapi.gcp.dto.SyntheticGcpData;
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
 * Verifies the GCP billing endpoint paginates (page size 4, 10 rows -> 3 pages)
 * and serves the nested GCP shape with vendor-accurate "nextPageToken" naming.
 */
@WebMvcTest(GcpBillingController.class)
@Import(SyntheticGcpData.class)
class GcpBillingControllerTest {

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
    void firstPage_hasNestedShape_andANextPageToken() throws Exception {
        mockMvc.perform(get("/gcp/billing-export"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows").isArray())
                .andExpect(jsonPath("$.rows[0].service.description").exists())
                .andExpect(jsonPath("$.rows[0].project.id").exists())
                .andExpect(jsonPath("$.rows[0].credits").isArray())
                .andExpect(jsonPath("$.rows[0].usage.pricing_unit").exists())
                // 10 rows, page size 4 -> first page not last, token = 4
                .andExpect(jsonPath("$.nextPageToken").value("4"));
    }

    @Test
    void lastPage_hasNoNextPageToken() throws Exception {
        // offset 8 of 10 -> rows 8,9 and no token
        mockMvc.perform(get("/gcp/billing-export").param("nextPageToken", "8"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows.length()").value(2))
                .andExpect(jsonPath("$.nextPageToken").doesNotExist());
    }

    @Test
    void garbageToken_startsFromBeginning() throws Exception {
        mockMvc.perform(get("/gcp/billing-export").param("nextPageToken", "junk"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0].project.id").value("northwind-platform"));
    }

    @Test
    void fullPaginationWalk_returnsAllTenRowsExactlyOnce() throws Exception {
        Set<String> seen = new LinkedHashSet<>();
        String token = "";
        int pages = 0;

        while (true) {
            MvcResult result = mockMvc.perform(get("/gcp/billing-export")
                            .param("nextPageToken", token))
                    .andExpect(status().isOk())
                    .andReturn();

            JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
            JsonNode rows = body.get("rows");
            for (int i = 0; i < rows.size(); i++) {
                JsonNode row = rows.get(i);
                // Build a stable identity from project + sku + usage_start_time
                seen.add(row.get("project").get("id").asText() + "|"
                        + row.get("sku").get("id").asText() + "|"
                        + row.get("usage_start_time").asText());
            }
            pages++;

            if (body.hasNonNull("nextPageToken")) {
                token = body.get("nextPageToken").asText();
            } else {
                break;
            }
            if (pages > 10) break;
        }

        assertThat(pages).isEqualTo(3);    // 4 + 4 + 2
        assertThat(seen).hasSize(10);      // no duplicates, no omissions
    }
}
