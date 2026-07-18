package io.finflow.recommendation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The recommendation-engine — the deferred "product layer" (Day 26).
 *
 * <p>Built last on purpose. By now the real shape of cost and commitment data is known
 * from having built normalization and commitment-tracking, so the rules are written
 * with knowledge instead of guesses. It consumes two streams —
 * {@code finflow.events.commitment} and {@code finflow.events.cost-normalized} — applies
 * three legible threshold rules (REBALANCE, PURCHASE_COMMITMENT, SELL), and emits
 * {@code RecommendationCreated} through the outbox to {@code finflow.events.recommendation},
 * which query-api projects for the dashboard's "top recommendations" panel.
 *
 * <p>Deliberately rules-based, not ML: the plan flags this as the piece most likely to
 * be over-engineered, and a rules engine a reviewer can read beats a model they must
 * trust. No {@code @EnableScheduling} here — every recommendation is event-driven; there
 * is no periodic job.
 */
@SpringBootApplication
public class RecommendationEngineApplication {
    public static void main(String[] args) {
        SpringApplication.run(RecommendationEngineApplication.class, args);
    }
}
