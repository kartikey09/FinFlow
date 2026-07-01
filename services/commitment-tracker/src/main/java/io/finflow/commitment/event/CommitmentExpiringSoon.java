package io.finflow.commitment.event;

import java.time.OffsetDateTime;

/**
 * Alert: a commitment will expire within the configured window (default 30 days).
 *
 * <p>Emitted by the daily expiry checker, not by the cost-event stream — there
 * is no incoming event that causes expiry, only the passage of time.
 */
public record CommitmentExpiringSoon(
        String commitmentId,
        String tenantId,
        String vendor,
        String accountId,
        OffsetDateTime expiresAt,
        long daysUntilExpiry
) {}
