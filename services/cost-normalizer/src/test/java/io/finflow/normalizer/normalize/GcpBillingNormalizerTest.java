package io.finflow.normalizer.normalize;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.finflow.normalizer.accounts.Account;
import io.finflow.normalizer.accounts.AccountLookupService;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * GCP nested payload → canonical mapping. Verifies the four GCP-specific things
 * Day 13 needs to get right: FX (cost * rate), the credits[] walk for the CUD
 * portion, list = cost - CUD (in USD), and commitment_id = the CUD credit name.
 */
class GcpBillingNormalizerTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final AccountLookupService accounts = mock(AccountLookupService.class);
    private final GcpBillingNormalizer normalizer = new GcpBillingNormalizer(mapper, accounts);

    private static final String JSON = """
            {
              "billing_account_id": "0123AB-CDEF45-6789GH",
              "service": { "id": "6F81-5844-456A", "description": "Compute Engine" },
              "sku":     { "id": "D0AB-1234-5678", "description": "N1 Core" },
              "project": { "id": "prod-app-42",    "name": "Prod App" },
              "usage_start_time": "2025-11-01T00:00:00Z",
              "usage_end_time":   "2025-11-01T01:00:00Z",
              "cost": 100.0,
              "currency": "INR",
              "currency_conversion_rate": 0.012,
              "usage": { "amount": 3600, "unit": "seconds",
                         "amount_in_pricing_units": 1.0, "pricing_unit": "hour" },
              "credits": [
                { "name": "cud-2025-11",  "amount": -12.0, "type": "COMMITTED_USAGE_DISCOUNT" },
                { "name": "sustained-1",  "amount":  -3.0, "type": "SUSTAINED_USAGE_DISCOUNT" }
              ],
              "cost_type": "regular"
            }
            """;

    @Test
    void mapsCanonicalFieldsWithFxAndCud() {
        Account stub = mock(Account.class);
        when(stub.getTeam()).thenReturn("platform");
        when(accounts.findOrUnknown(eq("0123AB-CDEF45-6789GH"))).thenReturn(stub);
        UUID eventId = UUID.randomUUID();

        CostLineItemNormalized n = normalizer.normalize(eventId, "default", JSON);

        assertThat(n.vendor()).isEqualTo("GCP");
        assertThat(n.rawSource()).isEqualTo("GCP");
        assertThat(n.accountId()).isEqualTo("0123AB-CDEF45-6789GH");
        assertThat(n.team()).isEqualTo("platform");
        assertThat(n.service()).isEqualTo("6F81-5844-456A");
        assertThat(n.resourceId()).isEqualTo("prod-app-42");                // GCP project

        // FX: 100 INR * 0.012 = 1.20 USD
        assertThat(n.costUsd()).isCloseTo(1.20, within(1e-9));

        // CUD is -12.0 INR -> -0.144 USD; list = cost - cud_usd = 1.20 - (-0.144) = 1.344 USD
        assertThat(n.listPriceUsd()).isCloseTo(1.344, within(1e-9));

        // Only the COMMITTED_USAGE_DISCOUNT credit names the commitment.
        assertThat(n.commitmentId()).isEqualTo("cud-2025-11");
        assertThat(n.lineItemType()).isEqualTo("regular");
    }

    @Test
    void rowWithoutCommittedUsageDiscountHasNullCommitmentAndListEqualsCost() {
        Account stub = mock(Account.class);
        when(stub.getTeam()).thenReturn("data");
        when(accounts.findOrUnknown(eq("0123AB-CDEF45-6789GH"))).thenReturn(stub);

        String onDemand = JSON.replace(
                "\"credits\": [\n" +
                "    { \"name\": \"cud-2025-11\",  \"amount\": -12.0, \"type\": \"COMMITTED_USAGE_DISCOUNT\" },\n" +
                "    { \"name\": \"sustained-1\",  \"amount\":  -3.0, \"type\": \"SUSTAINED_USAGE_DISCOUNT\" }\n" +
                "  ]",
                "\"credits\": []"
        );
        CostLineItemNormalized n = normalizer.normalize(UUID.randomUUID(), "default", onDemand);

        assertThat(n.commitmentId()).isNull();
        assertThat(n.costUsd()).isCloseTo(1.20, within(1e-9));
        assertThat(n.listPriceUsd()).isCloseTo(1.20, within(1e-9));        // no discount to add back
    }
}
