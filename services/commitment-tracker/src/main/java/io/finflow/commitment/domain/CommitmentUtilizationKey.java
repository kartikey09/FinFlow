package io.finflow.commitment.domain;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Composite primary key for {@link CommitmentUtilization}: one row per
 * commitment per day. Implements equals/hashCode per JPA's contract for
 * {@code @IdClass}.
 */
public class CommitmentUtilizationKey implements Serializable {

    private String commitmentId;
    private LocalDate periodDay;

    public CommitmentUtilizationKey() { /* for JPA */ }

    public CommitmentUtilizationKey(String commitmentId, LocalDate periodDay) {
        this.commitmentId = commitmentId;
        this.periodDay = periodDay;
    }

    public String getCommitmentId()   { return commitmentId; }
    public LocalDate getPeriodDay()   { return periodDay; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CommitmentUtilizationKey k)) return false;
        return Objects.equals(commitmentId, k.commitmentId) && Objects.equals(periodDay, k.periodDay);
    }

    @Override
    public int hashCode() {
        return Objects.hash(commitmentId, periodDay);
    }
}
