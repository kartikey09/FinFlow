package io.finflow.commitment.metrics;

import io.finflow.commitment.domain.CommitmentUtilization;
import io.finflow.commitment.domain.CommitmentUtilizationRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Day 22: {@code finflow.commitment.utilization}.
 *
 * <p>The plan names this metric explicitly. "Utilization" for a whole fleet of
 * commitments isn't a single number, so we expose it as two complementary
 * gauges under related names:
 * <ul>
 *   <li>{@code finflow.commitment.utilization} (tag {@code stat=avg}) — the mean
 *       {@code utilizationFraction()} across all commitments. The headline "are
 *       our commitments being used" number for the Business dashboard.</li>
 *   <li>{@code finflow.commitment.utilization} (tag {@code stat=underutilized_count})
 *       — how many commitments are below the underutilization threshold (0.50).
 *       This is the actionable one: it's what a FinOps team acts on (rebalance
 *       or let expire).</li>
 * </ul>
 *
 * <p>Both are recomputed on each Prometheus scrape from
 * {@code repository.findAll()}. The commitment table is small (one row per
 * commitment per day, and the tracker keeps only current commitments hot), so a
 * findAll on scrape is acceptable. If it grew, this would move to a projection
 * query — noted, not needed.
 *
 * <p>NOTE: the threshold here (0.50) mirrors the {@code underutilization.threshold-fraction}
 * in application.yml. It's duplicated as a constant rather than wired from
 * config to keep this metrics class dependency-light; if you change the yml
 * threshold, change it here too (called out in the Day 22 manifest).
 */
@Component
public class CommitmentMetrics {

    private static final String METRIC = "finflow.commitment.utilization";
    private static final double UNDERUTILIZED_THRESHOLD = 0.50;

    private final MeterRegistry meterRegistry;
    private final CommitmentUtilizationRepository repository;

    private final AtomicReference<Double> avgUtilization = new AtomicReference<>(0.0);
    private final AtomicInteger underutilizedCount = new AtomicInteger(0);

    public CommitmentMetrics(MeterRegistry meterRegistry, CommitmentUtilizationRepository repository) {
        this.meterRegistry = meterRegistry;
        this.repository = repository;
    }

    @PostConstruct
    void registerGauges() {
        Gauge.builder(METRIC, avgUtilization, AtomicReference::get)
                .description("Mean utilization fraction across all commitments")
                .tag("stat", "avg")
                .register(meterRegistry);

        Gauge.builder(METRIC, underutilizedCount, AtomicInteger::get)
                .description("Number of commitments below the underutilization threshold")
                .tag("stat", "underutilized_count")
                .register(meterRegistry);

        // Refresh gauge: recomputes both snapshots on each scrape.
        Gauge.builder("finflow.commitment.utilization.refresh", this, CommitmentMetrics::refreshAndReturnCount)
                .description("Internal: recomputes utilization snapshots on scrape")
                .register(meterRegistry);
    }

    private double refreshAndReturnCount() {
        List<CommitmentUtilization> all = repository.findAll();
        if (all.isEmpty()) {
            avgUtilization.set(0.0);
            underutilizedCount.set(0);
            return 0;
        }
        double sum = 0.0;
        int under = 0;
        for (CommitmentUtilization c : all) {
            double frac = c.utilizationFraction();
            sum += frac;
            if (frac < UNDERUTILIZED_THRESHOLD) {
                under++;
            }
        }
        avgUtilization.set(sum / all.size());
        underutilizedCount.set(under);
        return all.size();
    }
}
