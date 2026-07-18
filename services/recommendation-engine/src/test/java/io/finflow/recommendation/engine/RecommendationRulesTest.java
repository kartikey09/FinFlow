package io.finflow.recommendation.engine;

import io.finflow.recommendation.domain.CommitmentUnderutilized;
import io.finflow.recommendation.domain.RecommendationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * NO DOCKER, NO SPRING, NO DATABASE. Pure unit tests for the rules engine — the whole
 * reason the rule LOGIC was kept out of the Spring service. Every threshold and every
 * boundary is pinned here in milliseconds.
 *
 * <p>This is the resume artifact for Day 26: the rules are the "product" of the product
 * layer, and their correctness is asserted directly rather than inferred from a running
 * stack. It also runs on the MacBook that can't run Testcontainers, same as every other
 * no-Docker suite in this repo.
 */
class RecommendationRulesTest {

    // rebalanceStreak=3, sellStreak=14, purchaseMinDays=7, purchaseBreakEven=500, discount=0.30
    private final RecommendationRules rules = new RecommendationRules(3, 14, 7, 500.0, 0.30);

    private static CommitmentUnderutilized under(int streakDays, double observedFraction) {
        return new CommitmentUnderutilized(
                "cmt-1", "default", "AWS", "acct-1",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1).plusDays(streakDays),
                streakDays, 0.50, observedFraction);
    }

    @Nested
    @DisplayName("underutilization -> REBALANCE / SELL / nothing")
    class Classify {

        @Test
        @DisplayName("streak below rebalance threshold -> no recommendation yet")
        void belowThreshold() {
            assertThat(rules.classifyUnderutilization(under(2, 0.20))).isEmpty();
        }

        @Test
        @DisplayName("streak at the rebalance threshold (3) -> REBALANCE")
        void atRebalanceThreshold() {
            assertThat(rules.classifyUnderutilization(under(3, 0.20)))
                    .contains(RecommendationType.REBALANCE);
        }

        @Test
        @DisplayName("streak between rebalance and sell thresholds -> REBALANCE")
        void midRange() {
            assertThat(rules.classifyUnderutilization(under(10, 0.20)))
                    .contains(RecommendationType.REBALANCE);
        }

        @Test
        @DisplayName("one day BELOW the sell threshold -> still REBALANCE")
        void justBelowSell() {
            assertThat(rules.classifyUnderutilization(under(13, 0.05)))
                    .contains(RecommendationType.REBALANCE);
        }

        @Test
        @DisplayName("streak AT the sell threshold (14) -> SELL escalates over REBALANCE")
        void atSellThreshold() {
            assertThat(rules.classifyUnderutilization(under(14, 0.05)))
                    .contains(RecommendationType.SELL);
        }

        @Test
        @DisplayName("long-dead commitment -> SELL")
        void wellPastSell() {
            assertThat(rules.classifyUnderutilization(under(30, 0.00)))
                    .contains(RecommendationType.SELL);
        }
    }

    @Nested
    @DisplayName("underutilization savings estimate")
    class UnderSavings {

        @Test
        @DisplayName("more idle capacity -> more estimated savings (monotonic)")
        void moreIdleMoreSavings() {
            double mostlyIdle = rules.estimateUnderutilizationSavings(under(5, 0.10), 100.0);
            double slightlyIdle = rules.estimateUnderutilizationSavings(under(5, 0.80), 100.0);
            assertThat(mostlyIdle).isGreaterThan(slightlyIdle);
        }

        @Test
        @DisplayName("90% idle at $100/day -> ~0.90 * 100 * 30 = $2700/mo")
        void concreteNumber() {
            double savings = rules.estimateUnderutilizationSavings(under(5, 0.10), 100.0);
            assertThat(savings).isCloseTo(2700.0, within(0.01));
        }

        @Test
        @DisplayName("a fully-used commitment yields ~0 savings")
        void fullyUsedNoSavings() {
            double savings = rules.estimateUnderutilizationSavings(under(5, 1.00), 100.0);
            assertThat(savings).isCloseTo(0.0, within(0.01));
        }

        @Test
        @DisplayName("scale matters: same idle fraction, bigger commitment -> more savings")
        void scaleMatters() {
            double small = rules.estimateUnderutilizationSavings(under(5, 0.50), 100.0);
            double big = rules.estimateUnderutilizationSavings(under(5, 0.50), 1000.0);
            assertThat(big).isGreaterThan(small);
        }
    }

    @Nested
    @DisplayName("PURCHASE_COMMITMENT gate + savings")
    class Purchase {

        @Test
        @DisplayName("too few observed days -> no purchase reco even if spend is high")
        void tooFewDays() {
            assertThat(rules.shouldRecommendPurchase(6, 100_000.0)).isFalse();
        }

        @Test
        @DisplayName("enough days but below break-even spend -> no purchase reco")
        void belowBreakEven() {
            assertThat(rules.shouldRecommendPurchase(30, 499.99)).isFalse();
        }

        @Test
        @DisplayName("at both thresholds (7 days, $500) -> recommend purchase")
        void atThresholds() {
            assertThat(rules.shouldRecommendPurchase(7, 500.0)).isTrue();
        }

        @Test
        @DisplayName("purchase savings = 30% of observed on-demand spend")
        void purchaseSavings() {
            assertThat(rules.estimatePurchaseSavings(1000.0)).isCloseTo(300.0, within(0.01));
        }
    }

    @Test
    @DisplayName("thresholds are configurable — a stricter engine behaves differently on the same input")
    void configurableThresholds() {
        RecommendationRules strict = new RecommendationRules(5, 30, 14, 5000.0, 0.10);
        // streak of 3 fires REBALANCE on the default engine but nothing on the strict one.
        assertThat(rules.classifyUnderutilization(under(3, 0.2))).contains(RecommendationType.REBALANCE);
        assertThat(strict.classifyUnderutilization(under(3, 0.2))).isEmpty();
        // $600 clears the default break-even but not the strict one.
        assertThat(rules.shouldRecommendPurchase(20, 600.0)).isTrue();
        assertThat(strict.shouldRecommendPurchase(20, 600.0)).isFalse();
    }
}
