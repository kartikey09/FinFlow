package io.finflow.chaosapi.gcp.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The "usage" sub-object, e.g.
 * { "amount": 3600, "unit": "seconds", "amount_in_pricing_units": 1.0,
 *   "pricing_unit": "hour" }.
 *
 * Where AWS gave a single "lineItem/UsageAmount" number, GCP splits usage into
 * a raw amount+unit AND a pricing-normalized amount+unit. The normalizer will
 * generally want amountInPricingUnits (e.g. hours) rather than raw seconds.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GcpUsage(
        double amount,
        String unit,
        @JsonProperty("amount_in_pricing_units") double amountInPricingUnits,
        @JsonProperty("pricing_unit") String pricingUnit
) {}
