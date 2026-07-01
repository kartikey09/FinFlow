package io.finflow.commitment.tracker;

import io.finflow.commitment.consumer.CostNormalizedEvent;
import io.finflow.commitment.domain.Commitment;
import io.finflow.commitment.domain.CommitmentRepository;
import io.finflow.commitment.domain.CommitmentUtilization;
import io.finflow.commitment.domain.CommitmentUtilizationKey;
import io.finflow.commitment.domain.CommitmentUtilizationRepository;
import io.finflow.commitment.threshold.ThresholdEvaluator;
import io.finflow.outbox.OutboxAppender;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.OffsetDateTime;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit-level checks for the stateful folding:
 *   1. On-demand events (commitmentId == null) are ignored entirely.
 *   2. Events for unknown commitments are skipped (no NPE).
 *   3. A new (commitment, day) creates a row with available = daily ceiling.
 *   4. A second event the same day accumulates used_amount_usd.
 *   5. Crossing 100% emits CommitmentSaturated to the outbox.
 *   6. ThresholdEvaluator is delegated to on every applied event.
 *   7. usageStart's UTC date wins, even if the event is in another offset.
 */
class UtilizationTrackerTest {

    private final CommitmentRepository commitments = mock(CommitmentRepository.class);
    private final CommitmentUtilizationRepository utilization = mock(CommitmentUtilizationRepository.class);
    private final ThresholdEvaluator threshold = mock(ThresholdEvaluator.class);
    private final OutboxAppender outbox = mock(OutboxAppender.class);

    private final UtilizationTracker tracker =
            new UtilizationTracker(commitments, utilization, threshold, outbox, "default");

    @Test
    void onDemandEventIsIgnored() {
        CostNormalizedEvent e = new CostNormalizedEvent(null, 1.23, "2025-11-01T00:00:00Z", "default", "acct-1");

        tracker.apply(e, new RecordHeaders());

        verify(commitments, never()).findById(any());
        verify(utilization, never()).save(any());
    }

    @Test
    void unknownCommitmentIsSkipped() {
        CostNormalizedEvent e = new CostNormalizedEvent("nope", 1.23, "2025-11-01T00:00:00Z", "default", "acct-1");
        when(commitments.findById("nope")).thenReturn(Optional.empty());

        tracker.apply(e, new RecordHeaders());

        verify(utilization, never()).save(any());
        verify(threshold, never()).evaluate(any(), any());
    }

    @Test
    void firstEventCreatesUtilizationRow_ThresholdInvoked() {
        Commitment c = commitment("c-1", 100.0);
        CostNormalizedEvent e = new CostNormalizedEvent("c-1", 25.0, "2025-11-01T00:00:00Z", "default", "acct-1");
        when(commitments.findById("c-1")).thenReturn(Optional.of(c));
        when(utilization.findById(any(CommitmentUtilizationKey.class))).thenReturn(Optional.empty());

        tracker.apply(e, new RecordHeaders());

        ArgumentCaptor<CommitmentUtilization> saved = ArgumentCaptor.forClass(CommitmentUtilization.class);
        verify(utilization).save(saved.capture());
        assertThat(saved.getValue().getUsedAmountUsd()).isEqualTo(25.0);
        assertThat(saved.getValue().getAvailableUsd()).isEqualTo(100.0);
        assertThat(saved.getValue().getPeriodDay()).isEqualTo(LocalDate.of(2025, 11, 1));
        verify(threshold).evaluate(eq(c), any());
    }

    @Test
    void secondEventAccumulatesIntoExistingDayRow() {
        Commitment c = commitment("c-1", 100.0);
        CommitmentUtilization existing = new CommitmentUtilization("c-1", LocalDate.of(2025, 11, 1), 100.0);
        existing.increment(25.0, UUID.randomUUID());

        when(commitments.findById("c-1")).thenReturn(Optional.of(c));
        when(utilization.findById(any(CommitmentUtilizationKey.class))).thenReturn(Optional.of(existing));

        CostNormalizedEvent e = new CostNormalizedEvent("c-1", 30.0, "2025-11-01T12:00:00Z", "default", "acct-1");
        tracker.apply(e, new RecordHeaders());

        assertThat(existing.getUsedAmountUsd()).isEqualTo(55.0);
        assertThat(existing.utilizationFraction()).isEqualTo(0.55);
    }

    @Test
    void crossing100PercentEmitsSaturatedAlert() {
        Commitment c = commitment("c-1", 100.0);
        CommitmentUtilization day = new CommitmentUtilization("c-1", LocalDate.of(2025, 11, 1), 100.0);
        day.increment(80.0, UUID.randomUUID());

        when(commitments.findById("c-1")).thenReturn(Optional.of(c));
        when(utilization.findById(any(CommitmentUtilizationKey.class))).thenReturn(Optional.of(day));

        CostNormalizedEvent overflow = new CostNormalizedEvent("c-1", 25.0, "2025-11-01T12:00:00Z", "default", "acct-1");
        tracker.apply(overflow, new RecordHeaders());

        verify(outbox).append(eq("commitment"), eq("c-1"), eq("CommitmentSaturated"), any());
        assertThat(day.utilizationFraction()).isGreaterThan(1.0);
    }

    @Test
    void usageStartIsConvertedToUtcDate() {
        // 2025-11-01T01:00:00+02:00  -->  2025-10-31T23:00:00Z  -->  2025-10-31
        Commitment c = commitment("c-1", 100.0);
        when(commitments.findById("c-1")).thenReturn(Optional.of(c));
        when(utilization.findById(any(CommitmentUtilizationKey.class))).thenReturn(Optional.empty());

        CostNormalizedEvent e = new CostNormalizedEvent("c-1", 10.0, "2025-11-01T01:00:00+02:00", "default", "acct-1");
        tracker.apply(e, new RecordHeaders());

        ArgumentCaptor<CommitmentUtilization> saved = ArgumentCaptor.forClass(CommitmentUtilization.class);
        verify(utilization).save(saved.capture());
        assertThat(saved.getValue().getPeriodDay()).isEqualTo(LocalDate.of(2025, 10, 31));
    }

    private static Commitment commitment(String id, double daily) {
        Commitment c = mock(Commitment.class);
        when(c.getCommitmentId()).thenReturn(id);
        when(c.getDailyAmountUsd()).thenReturn(daily);
        when(c.getVendor()).thenReturn("AWS");
        when(c.getAccountId()).thenReturn("acct-1");
        return c;
    }
}
