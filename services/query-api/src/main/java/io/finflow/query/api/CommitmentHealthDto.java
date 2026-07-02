package io.finflow.query.api;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Wire shape for the commitment-health table. Flat and deliberately UI-shaped —
 * the entity's audit columns and internal ids are not exposed. If the UI ever
 * needs more, add columns here rather than leaking the JPA entity outward.
 */
public record CommitmentHealthDto(
        String commitmentId,
        String vendor,
        String accountId,
        String status,
        String lastAlertType,
        OffsetDateTime lastAlertAt,
        LocalDate latestPeriodDay,
        Double latestFraction,
        Double latestUsedUsd,
        Double latestAvailableUsd
) {}
