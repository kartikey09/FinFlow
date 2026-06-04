package io.finflow.chaosapi.aws.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The CUR field-name contract, tested with a bare ObjectMapper (the record's
 * own @JsonProperty / @JsonInclude annotations drive everything — no Spring).
 * The cheapest, fastest guard on the one detail that would silently break
 * Week-3 ingestion.
 */
class AwsCurLineItemSerializationTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void serializesToExactCurFieldNames() throws Exception {
        JsonNode json = mapper.readTree(mapper.writeValueAsString(sampleDiscountedUsage()));

        assertThat(json.has("identity/LineItemId")).isTrue();
        assertThat(json.has("identity/TimeInterval")).isTrue();
        assertThat(json.has("bill/PayerAccountId")).isTrue();
        assertThat(json.has("lineItem/UsageAccountId")).isTrue();
        assertThat(json.has("lineItem/LineItemType")).isTrue();
        assertThat(json.has("lineItem/UnblendedCost")).isTrue();
        assertThat(json.has("product/instanceType")).isTrue();
        assertThat(json.has("pricing/term")).isTrue();
        assertThat(json.has("reservation/EffectiveCost")).isTrue();
        assertThat(json.has("reservation/ReservationARN")).isTrue();
        assertThat(json.has("resourceTags/user:Team")).isTrue();
        assertThat(json.has("resourceTags/user:CostCenter")).isTrue();

        assertThat(json.has("lineItemId")).isFalse();
        assertThat(json.has("lineItemType")).isFalse();
        assertThat(json.has("tagTeam")).isFalse();
        assertThat(json.has("unblendedCost")).isFalse();
    }

    @Test
    void omitsNullFields_likeRealCur() throws Exception {
        AwsCurLineItem tax = new AwsCurLineItem(
                "01TAX", "2025-11-01T00:00:00Z/2025-12-01T00:00:00Z", "123456789012",
                "123456789012", "Tax", "2025-11-01T00:00:00Z", "2025-12-01T00:00:00Z",
                "AmazonEC2", "Tax", null, null, 0.0, 245.32,
                null, null, null, null, null, null, null, null);

        JsonNode json = mapper.readTree(mapper.writeValueAsString(tax));

        assertThat(json.has("lineItem/LineItemType")).isTrue();
        assertThat(json.has("reservation/EffectiveCost")).isFalse();
        assertThat(json.has("reservation/ReservationARN")).isFalse();
        assertThat(json.has("resourceTags/user:Team")).isFalse();
        assertThat(json.has("product/instanceType")).isFalse();
    }

    @Test
    void roundTrips_curJsonInThenCurJsonOut() throws Exception {
        String curJson = """
            {
              "identity/LineItemId": "01RT",
              "identity/TimeInterval": "2025-11-17T10:00:00Z/2025-11-17T11:00:00Z",
              "bill/PayerAccountId": "123456789012",
              "lineItem/UsageAccountId": "123456789013",
              "lineItem/LineItemType": "DiscountedUsage",
              "lineItem/UsageStartDate": "2025-11-17T10:00:00Z",
              "lineItem/UsageEndDate": "2025-11-17T11:00:00Z",
              "lineItem/ProductCode": "AmazonEC2",
              "lineItem/UsageType": "BoxUsage:p3.16xlarge",
              "lineItem/Operation": "RunInstances",
              "lineItem/ResourceId": "i-0abc",
              "lineItem/UsageAmount": 1.0,
              "lineItem/UnblendedCost": 0.0,
              "product/instanceType": "p3.16xlarge",
              "product/region": "us-east-1",
              "pricing/term": "Reserved",
              "pricing/publicOnDemandCost": 24.48,
              "reservation/EffectiveCost": 10.20,
              "reservation/ReservationARN": "arn:aws:ec2:us-east-1:123456789013:reserved-instances/ri-0abc123",
              "resourceTags/user:Team": "platform",
              "resourceTags/user:CostCenter": "CC-100"
            }
            """;

        AwsCurLineItem item = mapper.readValue(curJson, AwsCurLineItem.class);
        assertThat(item.lineItemId()).isEqualTo("01RT");
        assertThat(item.lineItemType()).isEqualTo("DiscountedUsage");
        assertThat(item.reservationEffectiveCost()).isEqualTo(10.20);
        assertThat(item.tagTeam()).isEqualTo("platform");

        JsonNode out = mapper.readTree(mapper.writeValueAsString(item));
        assertThat(out.get("identity/LineItemId").asText()).isEqualTo("01RT");
        assertThat(out.get("lineItem/LineItemType").asText()).isEqualTo("DiscountedUsage");
        assertThat(out.get("reservation/EffectiveCost").asDouble()).isEqualTo(10.20);
        assertThat(out.get("resourceTags/user:Team").asText()).isEqualTo("platform");
    }

    private AwsCurLineItem sampleDiscountedUsage() {
        return new AwsCurLineItem(
                "01TEST", "2025-11-17T10:00:00Z/2025-11-17T11:00:00Z", "123456789012",
                "123456789013", "DiscountedUsage", "2025-11-17T10:00:00Z",
                "2025-11-17T11:00:00Z", "AmazonEC2", "BoxUsage:p3.16xlarge",
                "RunInstances", "i-0abc", 1.0, 0.0,
                "p3.16xlarge", "us-east-1", "Reserved", 24.48,
                10.20, "arn:aws:ec2:us-east-1:123456789013:reserved-instances/ri-0abc123",
                "platform", "CC-100");
    }
}
