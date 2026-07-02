package io.finflow.query.projection.commitment;

import io.finflow.query.consumer.CommitmentEventConsumer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins down the alert-type -> projection-status mapping. If someone changes a
 * label upstream (or introduces a new alert type without a status) this catches
 * it before the dashboard silently drops the alert.
 */
class CommitmentEventConsumerStatusMapTest {

    @Test
    void mapsKnownAlertTypesToStatuses() {
        assertThat(CommitmentEventConsumer.statusFor("CommitmentUnderutilized")).isEqualTo("UNDERUTILIZED");
        assertThat(CommitmentEventConsumer.statusFor("CommitmentSaturated")).isEqualTo("SATURATED");
        assertThat(CommitmentEventConsumer.statusFor("CommitmentExpiringSoon")).isEqualTo("EXPIRING_SOON");
    }

    @Test
    void unknownAlertTypeReturnsNull() {
        assertThat(CommitmentEventConsumer.statusFor(null)).isNull();
        assertThat(CommitmentEventConsumer.statusFor("SomeFutureAlert")).isNull();
    }
}
