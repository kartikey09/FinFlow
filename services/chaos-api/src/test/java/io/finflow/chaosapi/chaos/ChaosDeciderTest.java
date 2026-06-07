package io.finflow.chaosapi.chaos;

import io.finflow.chaosapi.chaos.ChaosDecider;
import io.finflow.chaosapi.chaos.ChaosProperties;
import io.finflow.chaosapi.chaos.ChaosState;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit-tests the fault decision with fixed RNG inputs, so every branch is
 * deterministically reachable. No Spring, no HTTP — just the pure logic.
 *
 * We construct ChaosState directly from a ChaosProperties record, then drive
 * decide(roll, split) with hand-picked values.
 */
class ChaosDeciderTest {

    private ChaosDecider deciderWith(boolean enabled, int faultRate, double hangShare) {
        ChaosProperties props = new ChaosProperties(enabled, faultRate, hangShare, 5000);
        return new ChaosDecider(new ChaosState(props));
    }

    @Test
    void disabled_alwaysPasses_evenOnALowRoll() {
        ChaosDecider d = deciderWith(false, 100, 1.0);   // would fault if enabled
        assertThat(d.decide(0, 0.0)).isEqualTo(ChaosDecider.Outcome.PASS);
    }

    @Test
    void rollAtOrAboveRate_passes() {
        ChaosDecider d = deciderWith(true, 20, 0.5);
        // roll 20 with rate 20 is OUTSIDE the window (gate is roll < rate)
        assertThat(d.decide(20, 0.0)).isEqualTo(ChaosDecider.Outcome.PASS);
        assertThat(d.decide(99, 0.0)).isEqualTo(ChaosDecider.Outcome.PASS);
    }

    @Test
    void rollBelowRate_withLowSplit_hangs() {
        ChaosDecider d = deciderWith(true, 20, 0.5);
        // roll 5 < 20 -> fault; split 0.1 < 0.5 -> HANG
        assertThat(d.decide(5, 0.1)).isEqualTo(ChaosDecider.Outcome.HANG);
    }

    @Test
    void rollBelowRate_withHighSplit_returns503() {
        ChaosDecider d = deciderWith(true, 20, 0.5);
        // roll 5 < 20 -> fault; split 0.9 >= 0.5 -> 503
        assertThat(d.decide(5, 0.9)).isEqualTo(ChaosDecider.Outcome.FAIL_503);
    }

    @Test
    void splitExactlyAtShare_returns503_notHang() {
        ChaosDecider d = deciderWith(true, 20, 0.5);
        // boundary: split == hangShare is NOT < hangShare, so 503
        assertThat(d.decide(0, 0.5)).isEqualTo(ChaosDecider.Outcome.FAIL_503);
    }

    @Test
    void zeroRate_neverFaults() {
        ChaosDecider d = deciderWith(true, 0, 0.5);
        // roll 0 with rate 0: 0 >= 0 is true -> PASS (no roll can be < 0)
        assertThat(d.decide(0, 0.0)).isEqualTo(ChaosDecider.Outcome.PASS);
    }

    @Test
    void hundredRate_alwaysFaults() {
        ChaosDecider d = deciderWith(true, 100, 0.0);  // hangShare 0 -> all 503s
        assertThat(d.decide(0, 0.5)).isEqualTo(ChaosDecider.Outcome.FAIL_503);
        assertThat(d.decide(99, 0.5)).isEqualTo(ChaosDecider.Outcome.FAIL_503);
    }

    @Test
    void rejectsOutOfRangeFaultRate() {
        try {
            new ChaosProperties(true, 150, 0.5, 5000);
            assertThat(false).as("should have thrown").isTrue();
        } catch (IllegalArgumentException expected) {
            assertThat(expected).hasMessageContaining("fault-rate");
        }
    }
}
