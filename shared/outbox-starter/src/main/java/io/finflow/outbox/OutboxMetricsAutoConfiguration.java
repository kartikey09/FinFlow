package io.finflow.outbox;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

/**
 * Day 22: registers the {@code finflow.outbox.pending} gauge.
 *
 * <h2>Why a row count is the right "pending" signal here</h2>
 *
 * <p>In this architecture Debezium tails the Postgres WAL and never queries the
 * {@code outbox_event} table; the {@code OutboxRetentionJob} deletes rows on a
 * timer once they've been safely captured. So a row that's still present is a
 * row Debezium may not have shipped yet (or the retention sweep hasn't cleared).
 * {@code repository.count()} is therefore a good proxy for "outbox backlog" —
 * it should hover near zero and spike if Debezium stalls or Kafka is
 * unreachable. That spike is exactly what the Day-23 Reliability dashboard
 * alerts on.
 *
 * <p>The gauge reads {@code repository.count()} lazily every time Micrometer
 * scrapes it (default: whenever Prometheus polls {@code /actuator/prometheus}).
 * A COUNT(*) on a table that's kept near-empty by retention is cheap. If the
 * table were ever allowed to grow large, this should switch to an approximate
 * count (pg_class.reltuples) — noted, not needed today.
 *
 * <p>Guarded by {@code @ConditionalOnBean(MeterRegistry.class)} so it's inert
 * in a context without metrics.
 */
@AutoConfiguration
@ConditionalOnClass(MeterRegistry.class)
public class OutboxMetricsAutoConfiguration {

    @Bean
    @ConditionalOnBean(MeterRegistry.class)
    public Gauge outboxPendingGauge(MeterRegistry meterRegistry, OutboxEventRepository repository) {
        return Gauge.builder("finflow.outbox.pending", repository, r -> (double) r.count())
                .description("Rows currently in the outbox_event table (Debezium backlog + un-swept rows)")
                .register(meterRegistry);
    }
}
