package io.finflow.normalizer.normalize;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.finflow.normalizer.accounts.Account;
import io.finflow.normalizer.accounts.AccountLookupService;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * AWS CUR → canonical mapping, with account-metadata enrichment mocked.
 * The critical fields the plan calls out: USD cost (CUR is already USD), team
 * enrichment from the lookup, and commitment_id = reservation ARN.
 */
class AwsCurNormalizerTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final AccountLookupService accounts = mock(AccountLookupService.class);
    private final AwsCurNormalizer normalizer = new AwsCurNormalizer(mapper, accounts);

    private static final String JSON = """
            {
              "identity/LineItemId": "li-99",
              "bill/PayerAccountId": "999988887777",
              "lineItem/UsageAccountId": "111122223333",
              "lineItem/LineItemType": "DiscountedUsage",
              "lineItem/UsageStartDate": "2025-11-01T00:00:00Z",
              "lineItem/UsageEndDate":   "2025-11-01T01:00:00Z",
              "lineItem/ProductCode": "AmazonEC2",
              "lineItem/UsageType": "BoxUsage:m5.large",
              "lineItem/UnblendedCost": 0.096,
              "product/region": "us-east-1",
              "reservation/ReservationARN": "arn:aws:ec2:us-east-1:111122223333:ri/abc",
              "resourceTags/user:CostCenter": "platform"
            }
            """;

    @Test
    void mapsCanonicalFieldsAndEnrichesTeam() {
        Account stub = mock(Account.class);
        when(stub.getTeam()).thenReturn("platform");
        when(accounts.findOrUnknown(eq("111122223333"))).thenReturn(stub);
        UUID eventId = UUID.randomUUID();

        CostLineItemNormalized n = normalizer.normalize(eventId, "default", JSON);

        assertThat(n.eventId()).isEqualTo(eventId);
        assertThat(n.tenantId()).isEqualTo("default");
        assertThat(n.vendor()).isEqualTo("AWS");
        assertThat(n.rawSource()).isEqualTo("AWS");
        assertThat(n.accountId()).isEqualTo("111122223333");
        assertThat(n.team()).isEqualTo("platform");
        assertThat(n.service()).isEqualTo("AmazonEC2");
        assertThat(n.region()).isEqualTo("us-east-1");
        assertThat(n.usageStart()).isEqualTo("2025-11-01T00:00:00Z");
        assertThat(n.costUsd()).isEqualTo(0.096);
        assertThat(n.listPriceUsd()).isEqualTo(0.096);                 // AWS: list = cost
        assertThat(n.commitmentId()).isEqualTo("arn:aws:ec2:us-east-1:111122223333:ri/abc");
        assertThat(n.lineItemType()).isEqualTo("DiscountedUsage");
    }
}
