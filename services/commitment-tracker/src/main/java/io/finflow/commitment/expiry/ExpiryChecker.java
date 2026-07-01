package io.finflow.commitment.expiry;

import io.finflow.commitment.domain.Commitment;
import io.finflow.commitment.domain.CommitmentRepository;
import io.finflow.commitment.event.CommitmentExpiringSoon;
import io.finflow.outbox.OutboxAppender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Daily scan for commitments approaching expiry.
 *
 * <p>Why a scheduled job and not an event-driven rule? Because expiry is
 * triggered by the PASSAGE OF TIME, not by an incoming cost event. A commitment
 * could be receiving zero usage and still need a "30 days until it expires"
 * heads-up. The only stimulus is the clock.
 *
 * <p>This is a deliberately simple v1: emit an event for every commitment
 * inside the window, every run. A production version would carry a
 * {@code last_emitted_at} per commitment to avoid daily duplicates; for the
 * MVP (and Day 15's dashboard), one event per day per expiring commitment is
 * fine — Day 15's projection can dedupe by {@code commitment_id} for display.
 */
@Component
public class ExpiryChecker {

    private static final Logger log = LoggerFactory.getLogger(ExpiryChecker.class);

    private final CommitmentRepository commitmentRepository;
    private final OutboxAppender outboxAppender;
    private final int windowDays;
    private final String tenantId;

    public ExpiryChecker(CommitmentRepository commitmentRepository,
                         OutboxAppender outboxAppender,
                         @Value("${finflow.commitment.expiry.window-days:30}") int windowDays,
                         @Value("${finflow.tenant-id:default}") String tenantId) {
        this.commitmentRepository = commitmentRepository;
        this.outboxAppender = outboxAppender;
        this.windowDays = windowDays;
        this.tenantId = tenantId;
    }

    @Scheduled(cron = "${finflow.commitment.expiry.cron:0 0 4 * * *}")
    @Transactional
    public void emitExpiringSoonEvents() {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime threshold = now.plus(Duration.ofDays(windowDays));
        List<Commitment> expiring = commitmentRepository.findByExpiresAtBetween(now, threshold);

        for (Commitment c : expiring) {
            long daysUntil = ChronoUnit.DAYS.between(now, c.getExpiresAt());
            outboxAppender.append(
                    "commitment",
                    c.getCommitmentId(),
                    "CommitmentExpiringSoon",
                    new CommitmentExpiringSoon(
                            c.getCommitmentId(),
                            tenantId,
                            c.getVendor(),
                            c.getAccountId(),
                            c.getExpiresAt(),
                            daysUntil));
        }
        if (!expiring.isEmpty()) {
            log.info("Emitted CommitmentExpiringSoon for {} commitment(s) within {}d", expiring.size(), windowDays);
        }
    }
}
