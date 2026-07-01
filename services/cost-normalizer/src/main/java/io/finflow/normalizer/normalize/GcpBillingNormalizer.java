package io.finflow.normalizer.normalize;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.finflow.normalizer.accounts.Account;
import io.finflow.normalizer.accounts.AccountLookupService;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Turns a raw GCP billing-export row (nested JSON) into the canonical
 * {@link CostLineItemNormalized}.
 *
 * <p>The GCP-specific work the gcp-ingestor already did is RESPECTED here, not
 * redone:
 * <ul>
 *   <li>{@code cost} on the payload is in the account's local currency. The
 *       ingestor computes USD from {@code cost * currency_conversion_rate};
 *       we redo that multiplication here so the normalizer is self-contained
 *       (the ingestor's derived value isn't on the payload — only the raw
 *       fields are).</li>
 *   <li>{@code list_price_usd}: GCP's {@code COMMITTED_USAGE_DISCOUNT} credit
 *       is a NEGATIVE amount applied to cost. So
 *       {@code list = cost - committed_usage_discount}, which adds the
 *       discount back to recover the pre-CUD price.</li>
 *   <li>{@code commitment_id}: if a CUD credit is present, its {@code name} is
 *       the commitment identifier; otherwise the row is on-demand.</li>
 * </ul>
 */
@Component
public class GcpBillingNormalizer {

    private static final String COMMITTED_USAGE_DISCOUNT = "COMMITTED_USAGE_DISCOUNT";

    private final ObjectMapper objectMapper;
    private final AccountLookupService accounts;

    public GcpBillingNormalizer(ObjectMapper objectMapper, AccountLookupService accounts) {
        this.objectMapper = objectMapper;
        this.accounts = accounts;
    }

    public CostLineItemNormalized normalize(UUID eventId, String tenantId, String payloadJson) {
        try {
            JsonNode n = objectMapper.readTree(payloadJson);

            String accountId = text(n, "billing_account_id");
            Account account  = accounts.findOrUnknown(accountId);

            double cost = asDouble(n.get("cost"), 0.0);
            double rate = asDouble(n.get("currency_conversion_rate"), 1.0);
            double costUsd = cost * rate;

            CudPart cud = walkCommittedUsageDiscount(n.get("credits"));
            double listPriceUsd = costUsd - cud.amountUsd(rate);  // credits are negative; subtracting adds them back

            String service = textNested(n, "service", "id");
            String project = textNested(n, "project", "id");

            return new CostLineItemNormalized(
                    eventId,
                    tenantId,
                    "GCP",
                    accountId,
                    account.getTeam(),
                    service,
                    project,                                 // GCP project as resource scope
                    null,                                    // no top-level region in the export
                    text(n, "usage_start_time"),
                    text(n, "usage_end_time"),
                    asDoubleOrNull(textNested(n, "usage", "amount_in_pricing_units")),
                    costUsd,
                    listPriceUsd,
                    cud.commitmentId(),                      // null if no CUD credit on this row
                    text(n, "cost_type"),
                    "GCP");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to normalize GCP payload: " + e.getMessage(), e);
        }
    }

    /** Walk the credits array for the COMMITTED_USAGE_DISCOUNT credit, if any. */
    private CudPart walkCommittedUsageDiscount(JsonNode credits) {
        if (credits == null || !credits.isArray()) {
            return new CudPart(0.0, null);
        }
        double amount = 0.0;
        String commitmentId = null;
        for (JsonNode c : credits) {
            if (COMMITTED_USAGE_DISCOUNT.equals(text(c, "type"))) {
                amount += asDouble(c.get("amount"), 0.0);
                if (commitmentId == null) {
                    commitmentId = text(c, "name");
                }
            }
        }
        return new CudPart(amount, commitmentId);
    }

    /** Sum of committed-use-discount amounts (in local currency) and the commitment id. */
    private record CudPart(double amountLocal, String commitmentId) {
        double amountUsd(double rate) { return amountLocal * rate; }
    }

    private static String text(JsonNode n, String field) {
        if (n == null) return null;
        JsonNode v = n.get(field);
        return (v == null || v.isNull()) ? null : v.asText();
    }

    private static String textNested(JsonNode root, String parent, String field) {
        if (root == null) return null;
        JsonNode p = root.get(parent);
        return text(p, field);
    }

    private static double asDouble(JsonNode v, double fallback) {
        return (v == null || v.isNull()) ? fallback : v.asDouble();
    }

    private static Double asDoubleOrNull(String s) {
        if (s == null) return null;
        try { return Double.parseDouble(s); } catch (NumberFormatException e) { return null; }
    }
}
