package io.finflow.adapter.aws.chaos;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StepEndpointMapTest {

    private final StepEndpointMap map = new StepEndpointMap();

    @Test
    void acquireLock_mapsToLock() {
        assertThat(map.actionFor("ACQUIRE_LOCK")).isEqualTo("lock");
    }

    @Test
    void verifyCommitment_mapsToVerify() {
        assertThat(map.actionFor("VERIFY_COMMITMENT")).isEqualTo("verify");
    }

    @Test
    void reserveTarget_mapsToReserve() {
        assertThat(map.actionFor("RESERVE_TARGET")).isEqualTo("reserve");
    }

    @Test
    void releaseSource_mapsToRelease() {
        assertThat(map.actionFor("RELEASE_SOURCE")).isEqualTo("release");
    }

    @Test
    void updateLedger_mapsToUpdateLedger() {
        assertThat(map.actionFor("UPDATE_LEDGER")).isEqualTo("update-ledger");
    }

    @Test
    void unknownStep_returnsNull() {
        assertThat(map.actionFor("MADE_UP_STEP")).isNull();
    }

    @Test
    void nullStep_returnsNull() {
        assertThat(map.actionFor(null)).isNull();
    }
}
