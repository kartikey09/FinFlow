package io.finflow.ingestion.gcp.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Pure-Jackson + pure-logic checks (no Spring, no Docker) for the two pieces of
 * genuinely GCP-specific work: deserializing the NESTED export shape (objects +
 * a credits array), and the derivations on top of it — the committed-use-discount
 * walk and the USD conversion.
 */
class GcpBillingRowTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private static final String JSON = """
            {
              "billing_account_id": "0123AB-CDEF45-6789GH",
              "service": { "id": "6F81-5844-456A", "description": "Compute Engine" },
              "sku": { "id": "D0AB-1234-5678", "description": "N1 Predefined Instance Core" },
              "usage_start_time": "2025-11-01T00:00:00Z",
              "usage_end_time":   "2025-11-01T01:00:00Z",
              "project": { "id": "prod-app-42", "name": "Prod App" },
              "labels": [ { "key": "team", "value": "platform" } ],
              "cost": 100.0,
              "currency": "INR",
              "currency_conversion_rate": 0.012,
              "usage": { "amount": 3600, "unit": "seconds",
                         "amount_in_pricing_units": 1.0, "pricing_unit": "hour" },
              "credits": [
                { "name": "cud-1",      "amount": -12.0, "type": "COMMITTED_USAGE_DISCOUNT" },
                { "name": "sustained-1","amount":  -3.0, "type": "SUSTAINED_USAGE_DISCOUNT" }
              ],
              "cost_type": "regular",
              "some_future_column": "ignored"
            }
            """;

    @Test
    void deserializesNestedShape() throws Exception {
        GcpBillingRow row = mapper.readValue(JSON, GcpBillingRow.class);

        assertThat(row.billingAccountId()).isEqualTo("0123AB-CDEF45-6789GH");
        assertThat(row.service().description()).isEqualTo("Compute Engine");
        assertThat(row.sku().id()).isEqualTo("D0AB-1234-5678");
        assertThat(row.project().id()).isEqualTo("prod-app-42");
        assertThat(row.labels()).hasSize(1);
        assertThat(row.credits()).hasSize(2);
    }

    @Test
    void walksCreditsForCommittedUsageDiscountOnly() throws Exception {
        GcpBillingRow row = mapper.readValue(JSON, GcpBillingRow.class);
        // Only the COMMITTED_USAGE_DISCOUNT credit (-12.0) counts; the sustained one is ignored.
        assertThat(row.committedUsageDiscount()).isEqualTo(-12.0);
    }

    @Test
    void convertsCostToUsd() throws Exception {
        GcpBillingRow row = mapper.readValue(JSON, GcpBillingRow.class);
        // 100.0 INR * 0.012 = 1.20 USD
        assertThat(row.costInUsd()).isCloseTo(1.20, within(1e-9));
    }

    @Test
    void rowKeyIsDeterministic() throws Exception {
        GcpBillingRow a = mapper.readValue(JSON, GcpBillingRow.class);
        GcpBillingRow b = mapper.readValue(JSON, GcpBillingRow.class);
        assertThat(a.rowKey()).isEqualTo(b.rowKey());
    }
}
