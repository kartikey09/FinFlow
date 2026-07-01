package io.finflow.normalizer.normalize;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.finflow.normalizer.accounts.Account;
import io.finflow.normalizer.accounts.AccountLookupService;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Turns a raw AWS CUR line item (flat JSON with slashed keys) into the
 * canonical {@link CostLineItemNormalized}.
 *
 * <p>Independent model of the CUR contract — does not share a DTO with the
 * ingestor. We pluck only the fields the canonical schema needs, by their real
 * CUR column names. Unknown / missing fields become {@code null} rather than
 * throwing; the canonical record is tolerant of nullable enrichment columns.
 *
 * <p>FX: AWS CUR values are already USD, so {@code cost_usd = unblendedCost}
 * and {@code listPriceUsd = unblendedCost} (CUR doesn't carry a separate list
 * price in our subset; treat cost as list for AWS, since downstream commitment
 * math only cares about list vs. cost when there's a discount, and the
 * commitment side is GCP's CUD work).
 */
@Component
public class AwsCurNormalizer {

    private final ObjectMapper objectMapper;
    private final AccountLookupService accounts;

    public AwsCurNormalizer(ObjectMapper objectMapper, AccountLookupService accounts) {
        this.objectMapper = objectMapper;
        this.accounts = accounts;
    }

    public CostLineItemNormalized normalize(UUID eventId, String tenantId, String payloadJson) {
        try {
            JsonNode n = objectMapper.readTree(payloadJson);
            String accountId  = text(n, "lineItem/UsageAccountId");
            Account account   = accounts.findOrUnknown(accountId);
            Double unblended  = asDouble(n.get("lineItem/UnblendedCost"));
            String reservation = text(n, "reservation/ReservationARN");

            return new CostLineItemNormalized(
                    eventId,
                    tenantId,
                    "AWS",
                    accountId,
                    account.getTeam(),
                    text(n, "lineItem/ProductCode"),
                    null,                              // resource id not in our CUR subset
                    text(n, "product/region"),
                    text(n, "lineItem/UsageStartDate"),
                    text(n, "lineItem/UsageEndDate"),
                    null,                              // usage amount not in our CUR subset
                    unblended,                         // cost in USD
                    unblended,                         // list = cost for AWS (no separate list in our subset)
                    (reservation == null || reservation.isBlank()) ? null : reservation,
                    text(n, "lineItem/LineItemType"),
                    "AWS");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to normalize AWS payload: " + e.getMessage(), e);
        }
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return (v == null || v.isNull()) ? null : v.asText();
    }

    private static Double asDouble(JsonNode v) {
        return (v == null || v.isNull()) ? null : v.asDouble();
    }
}
