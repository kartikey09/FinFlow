package io.finflow.recommendation.config;

import io.finflow.recommendation.engine.RecommendationRules;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the pure {@link RecommendationRules} from configuration. The engine's logic
 * has no Spring in it (so it can be unit-tested); this is the one place the thresholds
 * cross from application.yml into that logic.
 */
@Configuration
public class RulesConfig {

    @Bean
    public RecommendationRules recommendationRules(
            @Value("${finflow.reco.rebalance-streak-days:3}") int rebalanceStreakDays,
            @Value("${finflow.reco.sell-streak-days:14}") int sellStreakDays,
            @Value("${finflow.reco.purchase.min-observed-days:7}") int purchaseMinObservedDays,
            @Value("${finflow.reco.purchase.break-even-usd:500.0}") double purchaseBreakEvenUsd,
            @Value("${finflow.reco.commitment-discount-fraction:0.30}") double commitmentDiscountFraction) {
        return new RecommendationRules(
                rebalanceStreakDays, sellStreakDays,
                purchaseMinObservedDays, purchaseBreakEvenUsd, commitmentDiscountFraction);
    }
}
