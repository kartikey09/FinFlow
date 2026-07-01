package io.finflow.commitment.event;

import java.time.LocalDate;

/**
 * Alert: a commitment hit 100%+ utilization on a given day — the spend has
 * already exceeded what the commitment covers.
 *
 * <p>Different alert philosophy from under-utilization: saturation is a
 * per-day event (no streak), because crossing the line ONCE is already the
 * signal that more reserved capacity may be needed.
 */
public record CommitmentSaturated(
        String commitmentId,
        String tenantId,
        String vendor,
        String accountId,
        LocalDate periodDay,
        double usedUsd,
        double availableUsd,
        double utilizationFraction
) {}
