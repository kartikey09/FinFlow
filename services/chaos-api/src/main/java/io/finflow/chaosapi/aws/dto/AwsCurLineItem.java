package io.finflow.chaosapi.aws.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One row of an AWS Cost and Usage Report (CUR v2), modeled faithfully enough
 * that the aws-ingestor (Week 3) parses it exactly as it would parse real AWS
 * data.
 *
 * You cannot put a slash (/) or a colon (:) in a Java variable
 * name because those are reserved symbols in the language; your code would completely fail to compile.
 * The @JsonProperty("...") annotation solves this. It acts as a bridge, telling the Jackson parser:
 * "When you see the string "identity/LineItemId" in the JSON, map its value to this Java variable named lineItemId."
 *
 * This is a representative subset of CUR's ~150 columns — only the fields the
 * downstream normalizer actually reads. Nullable fields (reservation*, tags)
 * are omitted from JSON when null via @JsonInclude(NON_NULL), mirroring how
 * real CUR omits inapplicable columns on a given row.
 */

@JsonInclude(JsonInclude.Include.NON_NULL)  /** this annotation guarantees that
 * any Double or String that is currently null will be completely stripped out of
 * the final JSON output, ensuring it  mimics a real AWS billing report.*/
public record AwsCurLineItem(

        @JsonProperty("identity/LineItemId")        String lineItemId,
        @JsonProperty("identity/TimeInterval")      String timeInterval,
        @JsonProperty("bill/PayerAccountId")        String payerAccountId,

        @JsonProperty("lineItem/UsageAccountId")    String usageAccountId,
        @JsonProperty("lineItem/LineItemType")      String lineItemType,
        @JsonProperty("lineItem/UsageStartDate")    String usageStartDate,
        @JsonProperty("lineItem/UsageEndDate")      String usageEndDate,
        @JsonProperty("lineItem/ProductCode")       String productCode,
        @JsonProperty("lineItem/UsageType")         String usageType,
        @JsonProperty("lineItem/Operation")         String operation,
        @JsonProperty("lineItem/ResourceId")        String resourceId,
        @JsonProperty("lineItem/UsageAmount")       Double usageAmount,  /**using object Double as it can be NULL instead of
 * primitive double which cannot be - its default is 0.0; for these rows we want the value to be non-existent instead of 0.0 */
        @JsonProperty("lineItem/UnblendedCost")     Double unblendedCost,

        @JsonProperty("product/instanceType")       String instanceType,
        @JsonProperty("product/region")             String region,

        @JsonProperty("pricing/term")               String pricingTerm,
        @JsonProperty("pricing/publicOnDemandCost") Double publicOnDemandCost,

        @JsonProperty("reservation/EffectiveCost")  Double reservationEffectiveCost,
        @JsonProperty("reservation/ReservationARN") String reservationArn,

        @JsonProperty("resourceTags/user:Team")       String tagTeam,
        @JsonProperty("resourceTags/user:CostCenter") String tagCostCenter

) {}