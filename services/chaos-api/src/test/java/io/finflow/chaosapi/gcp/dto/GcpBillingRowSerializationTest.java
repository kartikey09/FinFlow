package io.finflow.chaosapi.gcp.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The GCP serialization contract, tested with a bare ObjectMapper. Where the
 * AWS test guarded flat slash/colon field names, this guards the NESTED shape:
 * service/sku/project sub-objects and the credits[] array must serialize as
 * objects and arrays, and snake_case keys must round-trip.
 */
class GcpBillingRowSerializationTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void serializesNestedObjects_andSnakeCaseKeys() throws Exception {
        JsonNode json = mapper.readTree(mapper.writeValueAsString(sampleWithCud()));

        // Top-level snake_case keys
        assertThat(json.has("billing_account_id")).isTrue();
        assertThat(json.has("usage_start_time")).isTrue();
        assertThat(json.has("currency_conversion_rate")).isTrue();
        assertThat(json.has("cost_type")).isTrue();

        // Nested objects are objects, with their own fields
        assertThat(json.get("service").isObject()).isTrue();
        assertThat(json.get("service").get("description").asText()).isEqualTo("Compute Engine");
        assertThat(json.get("project").get("id").asText()).isEqualTo("finflow-platform");
        assertThat(json.get("usage").get("pricing_unit").asText()).isEqualTo("hour");

        // labels and credits are arrays
        assertThat(json.get("labels").isArray()).isTrue();
        assertThat(json.get("credits").isArray()).isTrue();
        assertThat(json.get("credits").get(0).get("type").asText())
                .isEqualTo("COMMITTED_USAGE_DISCOUNT");
    }

    @Test
    void emptyCreditsArray_survivesRoundTrip() throws Exception {
        GcpBillingRow onDemand = new GcpBillingRow(
                "01ABCD-234567-89EFGH",
                new GcpService("6F81-5844-456A", "Compute Engine"),
                new GcpSku("D0AA-3C5D-7B11", "A2 Instance Core running in Americas"),
                "2025-11-17T10:00:00Z", "2025-11-17T11:00:00Z",
                new GcpProject("finflow-ingestion", "FinFlow Ingestion"),
                List.of(new GcpLabel("team", "ingestion")),
                20.40, "USD", 1.0,
                new GcpUsage(3600, "seconds", 1.0, "hour"),
                List.of(),            // empty, not null — an on-demand row with no discount
                "regular");

        JsonNode json = mapper.readTree(mapper.writeValueAsString(onDemand));
        assertThat(json.get("credits").isArray()).isTrue();
        assertThat(json.get("credits")).isEmpty();
    }

    @Test
    void roundTrips_gcpJsonInThenGcpJsonOut() throws Exception {
        String gcpJson = """
            {
              "billing_account_id": "01ABCD-234567-89EFGH",
              "service": { "id": "6F81-5844-456A", "description": "Compute Engine" },
              "sku": { "id": "D0AA-3C5D-7B11", "description": "A2 Instance Core running in Americas" },
              "usage_start_time": "2025-11-17T10:00:00Z",
              "usage_end_time": "2025-11-17T11:00:00Z",
              "project": { "id": "finflow-platform", "name": "FinFlow Platform" },
              "labels": [ { "key": "team", "value": "platform" } ],
              "cost": 12.24,
              "currency": "USD",
              "currency_conversion_rate": 1.0,
              "usage": { "amount": 3600, "unit": "seconds", "amount_in_pricing_units": 1.0, "pricing_unit": "hour" },
              "credits": [ { "name": "Committed Usage Discount: A2 Core", "amount": -8.16, "type": "COMMITTED_USAGE_DISCOUNT" } ],
              "cost_type": "regular"
            }
            """;

        GcpBillingRow row = mapper.readValue(gcpJson, GcpBillingRow.class);
        assertThat(row.project().id()).isEqualTo("finflow-platform");
        assertThat(row.service().description()).isEqualTo("Compute Engine");
        assertThat(row.usage().amountInPricingUnits()).isEqualTo(1.0);
        assertThat(row.credits()).hasSize(1);
        assertThat(row.credits().get(0).type()).isEqualTo("COMMITTED_USAGE_DISCOUNT");
        assertThat(row.credits().get(0).amount()).isEqualTo(-8.16);

        // net cost = list cost + credits
        double net = row.cost() + row.credits().stream().mapToDouble(GcpCredit::amount).sum();
        assertThat(net).isEqualTo(4.08);

        // re-serialize: keys must be the identical GCP keys
        JsonNode out = mapper.readTree(mapper.writeValueAsString(row));
        assertThat(out.get("billing_account_id").asText()).isEqualTo("01ABCD-234567-89EFGH");
        assertThat(out.get("service").get("description").asText()).isEqualTo("Compute Engine");
        assertThat(out.get("credits").get(0).get("type").asText()).isEqualTo("COMMITTED_USAGE_DISCOUNT");
    }

    private GcpBillingRow sampleWithCud() {
        return new GcpBillingRow(
                "01ABCD-234567-89EFGH",
                new GcpService("6F81-5844-456A", "Compute Engine"),
                new GcpSku("D0AA-3C5D-7B11", "A2 Instance Core running in Americas"),
                "2025-11-17T10:00:00Z", "2025-11-17T11:00:00Z",
                new GcpProject("finflow-platform", "FinFlow Platform"),
                List.of(new GcpLabel("team", "platform"), new GcpLabel("cost_center", "CC-100")),
                12.24, "USD", 1.0,
                new GcpUsage(3600, "seconds", 1.0, "hour"),
                List.of(new GcpCredit("Committed Usage Discount: A2 Core", -8.16, "COMMITTED_USAGE_DISCOUNT")),
                "regular");
    }
}
