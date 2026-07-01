package io.finflow.normalizer.normalize.dispatch;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the shape-sniffing classifier picks the right vendor for representative
 * payloads — and the no-op cases. Pure Jackson, no Spring.
 */
class RawPayloadDispatcherTest {

    private final RawPayloadDispatcher dispatcher = new RawPayloadDispatcher(new ObjectMapper());

    @Test
    void awsPayloadIsClassifiedByIdentitySlashLineItemId() {
        String aws = """
                {
                  "identity/LineItemId": "li-1",
                  "lineItem/UsageAccountId": "111122223333",
                  "lineItem/UnblendedCost": 0.0464
                }""";
        assertThat(dispatcher.classify(aws)).isEqualTo(RawPayloadDispatcher.Vendor.AWS);
    }

    @Test
    void gcpPayloadIsClassifiedByNestedServiceObject() {
        String gcp = """
                {
                  "billing_account_id": "0123AB-CDEF45-6789GH",
                  "service": { "id": "6F81-5844-456A", "description": "Compute Engine" },
                  "cost": 100.0,
                  "currency_conversion_rate": 0.012
                }""";
        assertThat(dispatcher.classify(gcp)).isEqualTo(RawPayloadDispatcher.Vendor.GCP);
    }

    @Test
    void emptyOrInvalidPayloadIsUnknown() {
        assertThat(dispatcher.classify(null)).isEqualTo(RawPayloadDispatcher.Vendor.UNKNOWN);
        assertThat(dispatcher.classify("")).isEqualTo(RawPayloadDispatcher.Vendor.UNKNOWN);
        assertThat(dispatcher.classify("{not-json")).isEqualTo(RawPayloadDispatcher.Vendor.UNKNOWN);
        assertThat(dispatcher.classify("{\"foo\":\"bar\"}"))
                .isEqualTo(RawPayloadDispatcher.Vendor.UNKNOWN);
    }
}
