package io.finflow.commitment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * The commitment-tracker — the system's first STATEFUL stream processor.
 *
 * <p>Day 14's job: subscribe to {@code finflow.events.cost-normalized}, maintain
 * rolling per-day per-commitment utilization, and emit alert events when
 * threshold rules fire (under-utilization, saturation, expiring soon).
 *
 * <p>What's new here vs. cost-normalizer: this service is the first that has
 * to maintain DERIVED, CUMULATIVE state across events. Cost-normalizer was
 * stateless per event — one input row, one ledger row. Here a single input event
 * is folded into a rolling (commitment_id, period_day) counter, and a downstream
 * event is emitted ONLY when a temporal threshold rule transitions state.
 *
 * <p>{@code @EnableScheduling} for the expiry checker (a periodic job, since
 * "expiring soon" can't be detected from an incoming event — only by polling
 * time against {@code commitments.expires_at}).
 */
@SpringBootApplication
@EnableScheduling
public class CommitmentTrackerApplication {
    public static void main(String[] args) {
        SpringApplication.run(CommitmentTrackerApplication.class, args);
    }
}
