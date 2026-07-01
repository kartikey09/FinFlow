package io.finflow.commitment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * One row per commitment, tracking the current run of consecutive low-utilization
 * days. The under-utilization rule fires when {@code currentStreakDays} crosses
 * the configured threshold AND {@code lastEmittedStreakEndDay} is older than
 * {@code lastObservedDay} (i.e. we haven't already alerted for this streak).
 *
 * <p>Why a separate table? You can't derive "consecutive days" from the
 * utilization table without scanning every commitment's whole history on every
 * event. Carrying a small counter per commitment makes the check O(1).
 */
@Entity
@Table(name = "threshold_streaks")
public class ThresholdStreak {

    @Id
    @Column(name = "commitment_id", length = 128)
    private String commitmentId;

    @Column(name = "rule", length = 32, nullable = false)
    private String rule = "UNDERUTILIZATION";

    @Column(name = "current_streak_days", nullable = false)
    private int currentStreakDays = 0;

    @Column(name = "streak_start_day")
    private LocalDate streakStartDay;

    @Column(name = "last_observed_day")
    private LocalDate lastObservedDay;

    @Column(name = "last_emitted_streak_end_day")
    private LocalDate lastEmittedStreakEndDay;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    protected ThresholdStreak() { /* for JPA */ }

    public ThresholdStreak(String commitmentId) {
        this.commitmentId = commitmentId;
    }

    /** A low day extends the current streak (or starts a new one). */
    public void extendStreak(LocalDate day) {
        if (lastObservedDay == null || !day.equals(lastObservedDay.plusDays(1))) {
            // Either the first ever low day, or a non-contiguous low day
            // (e.g. a backfill jumping forward) — start a fresh streak.
            this.currentStreakDays = 1;
            this.streakStartDay = day;
        } else {
            this.currentStreakDays += 1;
        }
        this.lastObservedDay = day;
        this.updatedAt = OffsetDateTime.now();
    }

    /** Any non-low day resets the streak. */
    public void resetStreak(LocalDate day) {
        this.currentStreakDays = 0;
        this.streakStartDay = null;
        this.lastObservedDay = day;
        this.updatedAt = OffsetDateTime.now();
    }

    public void markEmitted(LocalDate day) {
        this.lastEmittedStreakEndDay = day;
        this.updatedAt = OffsetDateTime.now();
    }

    /** Has the current streak already produced an alert? */
    public boolean alreadyEmittedForCurrentStreak() {
        return lastEmittedStreakEndDay != null
                && streakStartDay != null
                && !lastEmittedStreakEndDay.isBefore(streakStartDay);
    }

    public String getCommitmentId()               { return commitmentId; }
    public int getCurrentStreakDays()             { return currentStreakDays; }
    public LocalDate getStreakStartDay()          { return streakStartDay; }
    public LocalDate getLastObservedDay()         { return lastObservedDay; }
    public LocalDate getLastEmittedStreakEndDay() { return lastEmittedStreakEndDay; }
}
