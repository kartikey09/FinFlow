package io.finflow.commitment.threshold;

import io.finflow.commitment.domain.Commitment;
import io.finflow.commitment.domain.CommitmentUtilization;
import io.finflow.commitment.domain.ThresholdStreak;
import io.finflow.commitment.domain.ThresholdStreakRepository;
import io.finflow.commitment.event.CommitmentUnderutilized;
import io.finflow.outbox.OutboxAppender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Evaluates the under-utilization rule on a per-event basis, maintaining the
 * {@code threshold_streaks} state for each commitment.
 *
 * <p>The rule: <em>if utilization &lt; threshold (default 50%) for N+ consecutive
 * days (default 3), emit {@code CommitmentUnderutilized}.</em>
 *
 * <p>The hard parts, and how each is handled:
 * <ul>
 *   <li><b>"Consecutive" over <i>event-recorded</i> days, not wall-clock.</b>
 *       A backfill of historical low days should not produce a fresh alert for
 *       every old day. The streak extends only when a low day is exactly one
 *       after the last observed day; an out-of-order jump resets to a length-1
 *       streak (handled by {@link ThresholdStreak#extendStreak}).</li>
 *   <li><b>"Below threshold" is judged on the (commitment, day) row as it
 *       stands right now</b> — i.e. after this event was folded in. So an event
 *       arriving late in the day that pushes utilization above the threshold
 *       correctly resets the streak; an event arriving early that doesn't push
 *       it past the threshold doesn't.</li>
 *   <li><b>Each streak produces at most ONE alert.</b> The
 *       {@code last_emitted_streak_end_day} column is the latch; we set it when
 *       we emit, and only emit again after a reset+re-extend has started a new
 *       streak.</li>
 * </ul>
 *
 * <p>{@code @Transactional(MANDATORY)} so this joins the tracker's transaction,
 * which means the streak update and any emitted alert commit atomically with
 * the underlying utilization row.
 */
@Service
public class ThresholdEvaluator {

    private static final Logger log = LoggerFactory.getLogger(ThresholdEvaluator.class);

    private final ThresholdStreakRepository streakRepository;
    private final OutboxAppender outboxAppender;
    private final double thresholdFraction;
    private final int streakDays;
    private final String tenantId;

    public ThresholdEvaluator(ThresholdStreakRepository streakRepository,
                              OutboxAppender outboxAppender,
                              @Value("${finflow.commitment.underutilization.threshold-fraction:0.50}") double thresholdFraction,
                              @Value("${finflow.commitment.underutilization.streak-days:3}") int streakDays,
                              @Value("${finflow.tenant-id:default}") String tenantId) {
        this.streakRepository = streakRepository;
        this.outboxAppender = outboxAppender;
        this.thresholdFraction = thresholdFraction;
        this.streakDays = streakDays;
        this.tenantId = tenantId;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void evaluate(Commitment commitment, CommitmentUtilization currentDay) {
        LocalDate day = currentDay.getPeriodDay();
        double fraction = currentDay.utilizationFraction();

        ThresholdStreak streak = streakRepository.findById(commitment.getCommitmentId())
                .orElseGet(() -> new ThresholdStreak(commitment.getCommitmentId()));

        boolean low = fraction < thresholdFraction;
        boolean wasFreshDay = streak.getLastObservedDay() == null || !day.equals(streak.getLastObservedDay());

        if (low) {
            // Only ADVANCE the streak on the first event for THIS day; subsequent
            // events the same day are just re-confirming the still-low state.
            if (wasFreshDay) {
                streak.extendStreak(day);
            }
        } else {
            // High day — reset (but only on the day we first see it cross up).
            if (wasFreshDay) {
                streak.resetStreak(day);
            }
        }

        if (low
                && streak.getCurrentStreakDays() >= streakDays
                && !streak.alreadyEmittedForCurrentStreak()) {
            outboxAppender.append(
                    "commitment",
                    commitment.getCommitmentId(),
                    "CommitmentUnderutilized",
                    new CommitmentUnderutilized(
                            commitment.getCommitmentId(),
                            tenantId,
                            commitment.getVendor(),
                            commitment.getAccountId(),
                            streak.getStreakStartDay(),
                            day,
                            streak.getCurrentStreakDays(),
                            thresholdFraction,
                            fraction));
            streak.markEmitted(day);
            log.info("Emitted CommitmentUnderutilized for {} ({} consecutive days, frac={})",
                    commitment.getCommitmentId(), streak.getCurrentStreakDays(), fraction);
        }

        streakRepository.save(streak);
    }
}
