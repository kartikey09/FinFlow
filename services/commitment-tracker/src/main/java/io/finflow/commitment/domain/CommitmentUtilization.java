package io.finflow.commitment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One day of utilization for one commitment. {@code used_amount_usd} is the
 * running total of {@code cost_usd} for events tagged with this commitment on
 * this day; {@code available_usd} is the daily ceiling captured at first-write
 * time (so an in-progress day's fraction is stable even if the master
 * {@code daily_amount_usd} were to change).
 *
 * <p>Insert-or-update: the tracker creates a row on the first event of a
 * (commitment, day) pair and increments {@code used_amount_usd} on every
 * subsequent event for that pair.
 */
@Entity
@Table(name = "commitment_utilization")
@IdClass(CommitmentUtilizationKey.class)
public class CommitmentUtilization {

    @Id
    @Column(name = "commitment_id", length = 128)
    private String commitmentId;

    @Id
    @Column(name = "period_day")
    private LocalDate periodDay;

    @Column(name = "used_amount_usd", nullable = false)
    private Double usedAmountUsd = 0.0;

    @Column(name = "available_usd", nullable = false)
    private Double availableUsd;

    @Column(name = "last_event_id")
    private UUID lastEventId;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    protected CommitmentUtilization() { /* for JPA */ }

    public CommitmentUtilization(String commitmentId, LocalDate periodDay, Double availableUsd) {
        this.commitmentId = commitmentId;
        this.periodDay = periodDay;
        this.availableUsd = availableUsd;
    }

    /** Fold a new cost event into this day's running total. */
    public void increment(Double costUsd, UUID eventId) {
        this.usedAmountUsd += costUsd == null ? 0.0 : costUsd;
        this.lastEventId = eventId;
        this.updatedAt = OffsetDateTime.now();
    }

    /** Utilization as a fraction in [0, +inf). Above 1.0 means the commitment was saturated. */
    public double utilizationFraction() {
        if (availableUsd == null || availableUsd == 0.0) {
            return 0.0;
        }
        return usedAmountUsd / availableUsd;
    }

    public String getCommitmentId()       { return commitmentId; }
    public LocalDate getPeriodDay()       { return periodDay; }
    public Double getUsedAmountUsd()      { return usedAmountUsd; }
    public Double getAvailableUsd()       { return availableUsd; }
    public UUID getLastEventId()          { return lastEventId; }
}
