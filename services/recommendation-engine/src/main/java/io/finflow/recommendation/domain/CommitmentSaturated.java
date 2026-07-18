package io.finflow.recommendation.domain;

import java.time.LocalDate;

/** Inbound event: a commitment is fully utilized (eventType "CommitmentSaturated"). */
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
