package io.finflow.commitment.event;

import java.time.LocalDate;

/**
 * Alert: commitment has been under-utilized for {@code streakDays}+ consecutive days.
 *
 * <p>Emitted via the outbox; routes to {@code finflow.events.commitment} with
 * {@code eventType=CommitmentUnderutilized}. Day 15 consumes and surfaces this
 * in the dashboard.
 *
 * <p>Schema is deliberately small — alerts should travel with just enough
 * context to be acted on. The full utilization history is queryable from the
 * database; the event carries the headline.
 */
public record CommitmentUnderutilized(
        String commitmentId,
        String tenantId,
        String vendor,
        String accountId,
        LocalDate streakStartDay,
        LocalDate streakEndDay,
        int streakDays,
        double thresholdFraction,            // e.g. 0.50
        double observedFractionLatestDay     // e.g. 0.27
) {}
