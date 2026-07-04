package io.finflow.adapter.gcp.chaos;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Pins the mapping — mirror of aws-adapter-worker's test. */
class StepEndpointMapTest {

    private final StepEndpointMap map = new StepEndpointMap();

    @Test
    void mapsAllFiveKnownSteps() {
        assertThat(map.actionFor("ACQUIRE_LOCK")).isEqualTo("lock");
        assertThat(map.actionFor("VERIFY_COMMITMENT")).isEqualTo("verify");
        assertThat(map.actionFor("RESERVE_TARGET")).isEqualTo("reserve");
        assertThat(map.actionFor("RELEASE_SOURCE")).isEqualTo("release");
        assertThat(map.actionFor("UPDATE_LEDGER")).isEqualTo("update-ledger");
    }

    @Test
    void unknownStepReturnsNull() {
        assertThat(map.actionFor(null)).isNull();
        assertThat(map.actionFor("")).isNull();
        assertThat(map.actionFor("NOT_A_STEP")).isNull();
    }
}
