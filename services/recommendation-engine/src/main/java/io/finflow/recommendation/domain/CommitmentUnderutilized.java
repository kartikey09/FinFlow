package io.finflow.recommendation.domain;

import java.time.LocalDate;

/**
 * Inbound event from commitment-tracker (finflow.events.commitment, eventType
 * "CommitmentUnderutilized"). Field-for-field identical to the emitter's record —
 * the engine and the tracker agree on the wire shape via JSON, not a shared class,
 * which is normal for independently-deployable services.
 */
public record CommitmentUnderutilized(
        String commitmentId,
        String tenantId,
        String vendor,
        String accountId,
        LocalDate streakStartDay,
        LocalDate streakEndDay,
        int streakDays,
        double thresholdFraction,
        double observedFractionLatestDay
) {}
