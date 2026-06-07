package io.finflow.chaosapi.gcp.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * One row of a GCP Cloud Billing export (the shape BigQuery writes to its
 * billing-export table), modeled faithfully enough that the gcp-ingestor
 * (Week 3) parses it exactly as it would parse a real export.
 *
 * The contrast with AWS is the whole point of Day 3. AWS CUR is FLAT — every
 * field is a top-level column like "lineItem/UsageType". GCP is NESTED — the
 * service, sku, project, and usage are sub-objects, and credits is an ARRAY of
 * objects. So where AwsCurLineItem used @JsonProperty to rename ~21 flat
 * fields, GcpBillingRow composes several small records and a List.
 *
 * GCP's field names ARE legal Java identifiers (no slashes/colons), so we need
 * far fewer @JsonProperty annotations than the AWS side — only where GCP uses
 * snake_case (e.g. "cost_type", "usage_start_time") that we render as camelCase
 * in Java.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GcpBillingRow(

        @JsonProperty("billing_account_id") String billingAccountId,
        GcpService service,
        GcpSku sku,
        @JsonProperty("usage_start_time") String usageStartTime,
        @JsonProperty("usage_end_time") String usageEndTime,
        GcpProject project,
        List<GcpLabel> labels,
        double cost,
        String currency,
        @JsonProperty("currency_conversion_rate") double currencyConversionRate,
        GcpUsage usage,
        List<GcpCredit> credits,
        @JsonProperty("cost_type") String costType

) {}
