package io.finflow.normalizer.normalize.dispatch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * Decides whether a {@code RawCostLineItem} payload came from AWS or GCP, by
 * shape.
 *
 * <p>The ingestors both emit {@code aggregate_type=billing}, {@code type=
 * RawCostLineItem}, and put each vendor's NATIVE shape into the JSON payload —
 * deliberately. So the dispatch lives here, on the consumer side, where it
 * inspects the JSON tree:
 * <ul>
 *   <li><b>AWS</b> is FLAT with slashed keys ({@code "identity/LineItemId"},
 *       {@code "lineItem/UsageType"}). Presence of {@code "identity/LineItemId"}
 *       is the unambiguous AWS marker.</li>
 *   <li><b>GCP</b> is NESTED — {@code service}, {@code sku}, {@code project}
 *       are objects. Presence of {@code "service"} as an OBJECT is the
 *       unambiguous GCP marker.</li>
 * </ul>
 *
 * <p>Why shape-sniff rather than route by header? It keeps Day 11/12 unchanged
 * (single topic, single aggregate_type, single event type) and the JSON shapes
 * are so structurally different that disambiguation is trivial. If a future
 * vendor's shape ever collides, switch to a header-based discriminator — it's a
 * one-line edit per ingestor (add a {@code Source} header to the append call).
 */
@Component
public class RawPayloadDispatcher {

    private final ObjectMapper objectMapper;

    public RawPayloadDispatcher(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Vendor classify(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) {
            return Vendor.UNKNOWN;
        }
        try {
            JsonNode tree = objectMapper.readTree(payloadJson);
            if (tree.has("identity/LineItemId")) {
                return Vendor.AWS;
            }
            JsonNode service = tree.get("service");
            if (service != null && service.isObject()) {
                return Vendor.GCP;
            }
            return Vendor.UNKNOWN;
        } catch (Exception e) {
            return Vendor.UNKNOWN;
        }
    }

    /** Which vendor format the raw payload belongs to. */
    public enum Vendor { AWS, GCP, UNKNOWN }
}
