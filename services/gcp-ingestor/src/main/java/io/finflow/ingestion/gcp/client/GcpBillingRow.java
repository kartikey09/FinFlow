package io.finflow.ingestion.gcp.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GcpBillingRow(

        @JsonProperty("billing_account_id") String billingAccountId,
        Service service,
        Sku sku,
        @JsonProperty("usage_start_time") String usageStartTime,
        @JsonProperty("usage_end_time") String usageEndTime,
        Project project,
        List<Label> labels,
        double cost,
        String currency,
        @JsonProperty("currency_conversion_rate") double currencyConversionRate,
        Usage usage,
        List<Credit> credits,
        @JsonProperty("cost_type") String costType

){
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Service(String id, String description) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Sku(String id, String description) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Project(String id, String name) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Label(String key, String value) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Usage(
            double amount,
            String unit,
            @JsonProperty("amount_in_pricing_units") double amountInPricingUnits,
            @JsonProperty("pricing_unit") String pricingUnit) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Credit(String name, double amount, String type) {}

    /** GCP credit type marking a committed-use discount (CUD). */
    public static final String COMMITTED_USAGE_DISCOUNT = "COMMITTED_USAGE_DISCOUNT";

    /**
     * Sum of committed-use-discount credits on this row. GCP credits are negative
     * (they reduce cost), so this is typically &le; 0. Other credit types
     * (sustained-use, promotions) are ignored — this is the CUD slice only.
     */
    public double committedUsageDiscount() {
        if (credits == null) {
            return 0.0;
        }
        return credits.stream()
                .filter(c -> COMMITTED_USAGE_DISCOUNT.equals(c.type()))
                .mapToDouble(Credit::amount)
                .sum();
    }

    /**
     * Convert the row's {@code cost} (in the billing account's currency) to USD
     * using {@code currency_conversion_rate}. A USD account has rate 1.0, so this
     * is a no-op there.
     */
    public double costInUsd() {
        return cost * currencyConversionRate;
    }

    /**
     * A deterministic dedup key. Unlike AWS CUR (which has identity/LineItemId),
     * GCP export rows have NO natural unique id, so we hash the identifying fields
     * into a stable UUID. Re-polling the same row (e.g. when the synthetic
     * dataset's nextPageToken loops back) yields the same key, so the idempotency
     * gate skips it.
     */
    public String rowKey() {
        String natural = String.join("|",
                nz(billingAccountId),
                service != null ? nz(service.id()) : "",
                sku != null ? nz(sku.id()) : "",
                project != null ? nz(project.id()) : "",
                nz(usageStartTime));
        return UUID.nameUUIDFromBytes(natural.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}