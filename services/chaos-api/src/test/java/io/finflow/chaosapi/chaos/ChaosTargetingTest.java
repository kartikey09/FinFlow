package io.finflow.chaosapi.chaos;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Day 20: per-endpoint chaos targeting.
 *
 * <p>Proves the property SagaCompensationIT depends on — "fail ONE step at 100%
 * while everything else stays healthy" — at the pure-logic level, no HTTP/Docker.
 * The IT exercises the same behavior end-to-end against real Postgres + Kafka.
 */
class ChaosTargetingTest {

    private ChaosState enabledState(int backgroundRate) {
        // hangShare 0 so a fault is deterministically a 503, never a hang.
        ChaosState state = new ChaosState(new ChaosProperties(true, backgroundRate, 0.0, 5000));
        return state;
    }

    @Test
    void disabled_effectiveRateIsAlwaysZero() {
        ChaosState state = new ChaosState(new ChaosProperties(false, 100, 0.0, 5000));
        state.setTargetPathContains("reserve");
        state.setTargetFaultRate(100);
        assertThat(state.effectiveFaultRate("/aws/reserve")).isZero();
    }

    @Test
    void noTargetFilter_allPathsSeeBackgroundRate() {
        ChaosState state = enabledState(20);
        assertThat(state.effectiveFaultRate("/aws/reserve")).isEqualTo(20);
        assertThat(state.effectiveFaultRate("/aws/anything-else")).isEqualTo(20);
    }

    @Test
    void targetFilter_onlyMatchingUriGetsElevatedRate() {
        ChaosState state = enabledState(0);          // background 0% — everything healthy...
        state.setTargetPathContains("reserve");
        state.setTargetFaultRate(100);               // ...except the reserve step at 100%

        assertThat(state.effectiveFaultRate("/aws/reserve-target")).isEqualTo(100);
        assertThat(state.effectiveFaultRate("/aws/acquire-lock")).isZero();
        assertThat(state.effectiveFaultRate("/aws/verify")).isZero();
    }

    @Test
    void clearTarget_fallsBackToBackground() {
        ChaosState state = enabledState(0);
        state.setTargetPathContains("reserve");
        state.setTargetFaultRate(100);
        state.clearTarget();
        assertThat(state.effectiveFaultRate("/aws/reserve")).isZero();
        assertThat(state.targetPathContains()).isNull();
    }

    @Test
    void decider_faultsOnlyTheTargetedPath() {
        ChaosState state = enabledState(0);
        state.setTargetPathContains("reserve");
        state.setTargetFaultRate(100);
        ChaosDecider decider = new ChaosDecider(state);

        // roll 0, split 0.9: for the targeted path rate=100 -> fault -> 503 (hangShare 0)
        assertThat(decider.decide("/aws/reserve", 0, 0.9))
                .isEqualTo(ChaosDecider.Outcome.FAIL_503);
        // a non-target path sees background 0 -> always PASS
        assertThat(decider.decide("/aws/acquire-lock", 0, 0.9))
                .isEqualTo(ChaosDecider.Outcome.PASS);
    }
}
