package io.finflow.recommendation.domain;

/**
 * The three v1 recommendation kinds, straight from the build plan.
 *
 * <p>Deliberately rule-based, not ML. The plan is explicit that this is the piece
 * most likely to be over-engineered, and that a clear rules engine demonstrating the
 * architecture beats a model that obscures it. Each value below is one legible
 * threshold rule a reviewer can read and immediately understand.
 */
public enum RecommendationType {

    /** A commitment is underutilized while matching on-demand spend exists elsewhere — move it. */
    REBALANCE,

    /** Sustained on-demand spend has passed the break-even point for buying a commitment. */
    PURCHASE_COMMITMENT,

    /** A commitment has sat unused for 14+ consecutive days with no recovery — sell it. */
    SELL
}
