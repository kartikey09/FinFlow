package io.finflow.commitment.threshold;

import io.finflow.commitment.domain.Commitment;
import io.finflow.commitment.domain.CommitmentUtilization;
import io.finflow.commitment.domain.ThresholdStreak;
import io.finflow.commitment.domain.ThresholdStreakRepository;
import io.finflow.outbox.OutboxAppender;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The consecutive-day under-utilization rule is where Day 14's most subtle
 * logic lives. Tested here for the five behaviors that matter:
 *   1. ONE low day -> streak=1, NO alert.
 *   2. TWO consecutive low days -> streak=2, NO alert (threshold is 3).
 *   3. THREE consecutive low days -> streak=3, ALERT.
 *   4. Repeat events on day 3 don't re-fire (latch).
 *   5. A high day in the middle resets the streak.
 *
 * Threshold is parameterized to 0.50 / streakDays=3 to match the production
 * defaults.
 */
class ThresholdEvaluatorTest {

    private final ThresholdStreakRepository streaks = mock(ThresholdStreakRepository.class);
    private final OutboxAppender outbox = mock(OutboxAppender.class);
    private final ThresholdEvaluator evaluator =
            new ThresholdEvaluator(streaks, outbox, 0.50, 3, "default");

    private final Commitment commitment = commitment("c-1");

    @Test
    void oneLowDayDoesNotEmit() {
        when(streaks.findById("c-1")).thenReturn(Optional.empty());

        evaluator.evaluate(commitment, util(LocalDate.of(2025, 11, 1), 0.20));

        verify(outbox, never()).append(any(), any(), any(), any());
    }

    @Test
    void twoConsecutiveLowDaysDoNotEmit() {
        ThresholdStreak streak = new ThresholdStreak("c-1");
        streak.extendStreak(LocalDate.of(2025, 11, 1));   // streak=1 already
        when(streaks.findById("c-1")).thenReturn(Optional.of(streak));

        evaluator.evaluate(commitment, util(LocalDate.of(2025, 11, 2), 0.20));

        assertThat(streak.getCurrentStreakDays()).isEqualTo(2);
        verify(outbox, never()).append(any(), any(), any(), any());
    }

    @Test
    void thirdConsecutiveLowDayEmits() {
        ThresholdStreak streak = new ThresholdStreak("c-1");
        streak.extendStreak(LocalDate.of(2025, 11, 1));
        streak.extendStreak(LocalDate.of(2025, 11, 2));   // streak=2
        when(streaks.findById("c-1")).thenReturn(Optional.of(streak));

        evaluator.evaluate(commitment, util(LocalDate.of(2025, 11, 3), 0.20));

        assertThat(streak.getCurrentStreakDays()).isEqualTo(3);
        verify(outbox).append(eq("commitment"), eq("c-1"), eq("CommitmentUnderutilized"), any());
    }

    @Test
    void repeatEventsOnTheEmittedDayDoNotReFire() {
        ThresholdStreak streak = new ThresholdStreak("c-1");
        // Simulate state right after the alert: streak=3, latch set for day 3.
        streak.extendStreak(LocalDate.of(2025, 11, 1));
        streak.extendStreak(LocalDate.of(2025, 11, 2));
        streak.extendStreak(LocalDate.of(2025, 11, 3));
        streak.markEmitted(LocalDate.of(2025, 11, 3));
        when(streaks.findById("c-1")).thenReturn(Optional.of(streak));

        // A SECOND event on the same day — utilization still low.
        evaluator.evaluate(commitment, util(LocalDate.of(2025, 11, 3), 0.30));

        verify(outbox, never()).append(any(), any(), any(), any());
    }

    @Test
    void aHighDayResetsTheStreak() {
        ThresholdStreak streak = new ThresholdStreak("c-1");
        streak.extendStreak(LocalDate.of(2025, 11, 1));
        streak.extendStreak(LocalDate.of(2025, 11, 2));   // streak=2
        when(streaks.findById("c-1")).thenReturn(Optional.of(streak));

        // Day 3 is HIGH (0.80 >= 0.50).
        evaluator.evaluate(commitment, util(LocalDate.of(2025, 11, 3), 0.80));

        assertThat(streak.getCurrentStreakDays()).isEqualTo(0);
        verify(outbox, never()).append(any(), any(), any(), any());
    }

    private static Commitment commitment(String id) {
        Commitment c = mock(Commitment.class);
        when(c.getCommitmentId()).thenReturn(id);
        when(c.getVendor()).thenReturn("AWS");
        when(c.getAccountId()).thenReturn("acct-1");
        return c;
    }

    /** Build a utilization row with a given fraction by setting used = 100*fraction, available = 100. */
    private static CommitmentUtilization util(LocalDate day, double fraction) {
        CommitmentUtilization u = new CommitmentUtilization("c-1", day, 100.0);
        u.increment(fraction * 100.0, null);
        return u;
    }
}
