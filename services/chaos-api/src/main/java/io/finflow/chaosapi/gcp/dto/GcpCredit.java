package io.finflow.chaosapi.gcp.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One entry of the "credits" array, e.g.
 * { "name": "Committed Usage Discount: CPU", "amount": -8.40,
 *   "type": "COMMITTED_USAGE_DISCOUNT" }.
 *
 * This is the single most important structural difference from AWS, and the
 * reason the credits[] array exists on the row at all.
 *
 * In AWS, a commitment discount shows up as a SEPARATE LINE with its own
 * LineItemType (DiscountedUsage) and a reservation/EffectiveCost column. In
 * GCP, the on-demand cost and the discount live on the SAME row: `cost` is the
 * list price, and a negative-amount credit of type COMMITTED_USAGE_DISCOUNT
 * sits in this array. Net cost = cost + sum(credit.amount). The normalizer
 * must sum the credits to find the real cost — it can't read a single
 * "effective cost" field the way it can for AWS.
 *
 * Common GCP credit types: COMMITTED_USAGE_DISCOUNT, SUSTAINED_USAGE_DISCOUNT,
 * DISCOUNT, PROMOTION, FREE_TIER.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)      // if any field is null in this java object, completely exclude that key
public record GcpCredit(                        // from the final JSON output
        String name,
        double amount,
        String type
) {}
