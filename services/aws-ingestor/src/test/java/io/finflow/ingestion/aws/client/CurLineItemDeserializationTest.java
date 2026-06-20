package io.finflow.ingestion.aws.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure-Jackson check (no Spring, no Docker) that our DTO mirrors the real CUR
 * column names — slashes and the {@code user:CostCenter} colon included. If the
 * Chaos API's field names and these {@code @JsonProperty} keys ever drift apart,
 * this fails fast instead of silently deserializing nulls.
 */
class CurLineItemDeserializationTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void mapsRealCurColumnNames() throws Exception {
        String json = """
                {
                  "identity/LineItemId": "li-123",
                  "bill/PayerAccountId": "999988887777",
                  "lineItem/UsageAccountId": "111122223333",
                  "lineItem/LineItemType": "DiscountedUsage",
                  "lineItem/ProductCode": "AmazonEC2",
                  "lineItem/UnblendedCost": 0.0,
                  "product/region": "us-east-1",
                  "reservation/ReservationARN": "arn:aws:ec2:us-east-1:111122223333:reserved-instances/abc",
                  "resourceTags/user:CostCenter": "platform",
                  "some/UnknownFutureColumn": "ignored"
                }
                """;

        CurLineItem item = mapper.readValue(json, CurLineItem.class);

        assertThat(item.lineItemId()).isEqualTo("li-123");
        assertThat(item.usageAccountId()).isEqualTo("111122223333");
        assertThat(item.lineItemType()).isEqualTo("DiscountedUsage");
        assertThat(item.productCode()).isEqualTo("AmazonEC2");
        assertThat(item.region()).isEqualTo("us-east-1");
        assertThat(item.tagCostCenter()).isEqualTo("platform");
        assertThat(item.reservationArn()).startsWith("arn:aws:ec2:");
    }
}

