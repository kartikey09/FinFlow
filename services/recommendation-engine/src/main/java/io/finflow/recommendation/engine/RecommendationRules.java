package io.finflow.recommendation.engine;

import io.finflow.recommendation.domain.CommitmentUnderutilized;
import io.finflow.recommendation.domain.RecommendationType;

import java.util.Optional;

/**
 * The rules engine — a PURE function from a signal to "should we recommend something,
 * and how much is it worth". No Spring, no database, no wall clock. That's what makes
 * RecommendationRulesTest able to pin every threshold exhaustively in milliseconds.
 *
 * <p>All three rules are deliberately simple and legible. The build plan is explicit:
 * this is the piece most likely to be over-engineered, and a rules engine a reviewer
 * can read in thirty seconds is worth more here than a model they have to trust.
 *
 * <p>The thresholds are constructor parameters, not hard-coded, so the same logic is
 * driven by application.yml in production and by explicit values in tests.
 */
public final class RecommendationRules {

    /** A commitment underutilized this many days should be REBALANCED (move it to where the demand is). */
    private final int rebalanceStreakDays;
    /** A commitment underutilized this many days with no recovery should be SOLD, not just moved. */
    private final int sellStreakDays;
    /** PURCHASE_COMMITMENT: sustained on-demand spend must span at least this many observed days. */
    private final int purchaseMinObservedDays;
    /** ...and exceed this cumulative dollar amount (the break-even proxy). */
    private final double purchaseBreakEvenUsd;
    /** Fraction of on-demand spend a commitment is assumed to save (list -> committed discount proxy). */
    private final double commitmentDiscountFraction;

    public RecommendationRules(int rebalanceStreakDays, int sellStreakDays,
                               int purchaseMinObservedDays, double purchaseBreakEvenUsd,
                               double commitmentDiscountFraction) {
        this.rebalanceStreakDays = rebalanceStreakDays;
        this.sellStreakDays = sellStreakDays;
        this.purchaseMinObservedDays = purchaseMinObservedDays;
        this.purchaseBreakEvenUsd = purchaseBreakEvenUsd;
        this.commitmentDiscountFraction = commitmentDiscountFraction;
    }

    /**
     * Classify an underutilization signal.
     *
     * <p>SELL wins over REBALANCE once the streak is long enough: a commitment nobody
     * has used for two weeks isn't a rebalancing opportunity, it's dead weight to shed.
     * Below the sell threshold but above the rebalance threshold, it's a REBALANCE.
     * Below both, no recommendation (the streak is too short to act on yet).
     */
    public Optional<RecommendationType> classifyUnderutilization(CommitmentUnderutilized e) {
        if (e.streakDays() >= sellStreakDays) {
            return Optional.of(RecommendationType.SELL);
        }
        if (e.streakDays() >= rebalanceStreakDays) {
            return Optional.of(RecommendationType.REBALANCE);
        }
        return Optional.empty();
    }

    /**
     * Estimated monthly savings from acting on an underutilized commitment.
     *
     * <p>The wasted portion is the idle fraction of the commitment. We approximate the
     * commitment's daily value from what's being wasted and annualize to a 30-day month.
     * Rough on purpose — it's a ranking signal for the dashboard, not an invoice. The
     * point is that a 90%-idle $1000/day commitment ranks above a 55%-idle $100/day one.
     */
    public double estimateUnderutilizationSavings(CommitmentUnderutilized e, double dailyCommitmentUsd) {
        double idleFraction = Math.max(0.0, 1.0 - e.observedFractionLatestDay());
        double wastedPerDay = dailyCommitmentUsd * idleFraction;
        return round2(wastedPerDay * 30.0);
    }

    /**
     * PURCHASE_COMMITMENT fires when on-demand spend for an (account, service) has been
     * sustained — enough distinct days AND enough cumulative dollars to clear the
     * break-even of actually buying a commitment.
     */
    public boolean shouldRecommendPurchase(int observedDays, double totalOndemandUsd) {
        return observedDays >= purchaseMinObservedDays
                && totalOndemandUsd >= purchaseBreakEvenUsd;
    }

    /**
     * Estimated savings from converting sustained on-demand spend to a commitment:
     * the assumed committed-use discount applied to the spend observed so far.
     */
    public double estimatePurchaseSavings(double totalOndemandUsd) {
        return round2(totalOndemandUsd * commitmentDiscountFraction);
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
