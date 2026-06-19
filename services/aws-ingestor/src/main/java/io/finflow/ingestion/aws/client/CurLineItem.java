package io.finflow.ingestion.aws.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One AWS Cost and Usage Report (CUR v2) line item, as the ingestor sees it.
 *
 * <p>This is the ingestor's OWN model of the external contract — deliberately not
 * a shared type with the Chaos API. A real ingestor models the cloud vendor's
 * response independently; coupling to the producer's class would be wrong. The
 * {@code @JsonProperty} keys carry the real CUR column names (with {@code /} and
 * {@code :}, which aren't legal in Java identifiers).
 *
 * <p>A representative subset of CUR's ~150 columns — just the fields downstream
 * actually reads: identity, the usage account, line-item type (for commitment
 * classification), the time window, product/usage, cost, region, the reservation
 * ARN, and the cost-center tag. {@code @JsonIgnoreProperties(ignoreUnknown=true)}
 * means extra columns are tolerated, so this never breaks if CUR adds fields.
 *
 * <p>This object is also the outbox event payload for {@code RawCostLineItem}:
 * the appender serializes it to JSON verbatim, so the event carries the raw CUR
 * shape and the normalizer (Day 13) does the interpreting.
 */

@JsonIgnoreProperties(ignoreUnknown = true)
public record CurLineItem (
        @JsonProperty("identity/LineItemId")          String lineItemId,
        @JsonProperty("bill/PayerAccountId")          String payerAccountId,
        @JsonProperty("lineItem/UsageAccountId")      String usageAccountId,
        @JsonProperty("lineItem/LineItemType")        String lineItemType,
        @JsonProperty("lineItem/UsageStartDate")      String usageStartDate,
        @JsonProperty("lineItem/UsageEndDate")        String usageEndDate,
        @JsonProperty("lineItem/ProductCode")         String productCode,
        @JsonProperty("lineItem/UsageType")           String usageType,
        @JsonProperty("lineItem/UnblendedCost")       Double unblendedCost,
        @JsonProperty("product/region")               String region,
        @JsonProperty("reservation/ReservationARN")   String reservationArn,
        @JsonProperty("resourceTags/user:CostCenter") String tagCostCenter
) {}
