package io.finflow.outbox;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure unit test of the retention job's policy — no broker, no DB, no Docker.
 * Verifies it asks the repository to delete using a cutoff equal to
 * (now - retention window). The actual SQL delete is the repository's job and
 * is exercised by the application against real Postgres.
 */

public class OutboxRetentionJobTest {
    private final OutboxEventRepository repository = mock(OutboxEventRepository.class);

    @Test
    void purgesUsingASevenDayCutoffByDefault() {
        when(repository.deleteCreatedBefore(any())).thenReturn(5);
        OutboxRetentionJob job = new OutboxRetentionJob(repository, Duration.ofDays(7));

        job.purgeExpiredEvents();

        ArgumentCaptor<OffsetDateTime> cutoff = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(repository).deleteCreatedBefore(cutoff.capture());
        assertThat(cutoff.getValue())
                .isCloseTo(OffsetDateTime.now().minusDays(7), within(1, ChronoUnit.MINUTES));
    }

    @Test
    void honoursAConfiguredRetentionWindow() {
        when(repository.deleteCreatedBefore(any())).thenReturn(0);
        OutboxRetentionJob job = new OutboxRetentionJob(repository, Duration.ofHours(12));

        job.purgeExpiredEvents();

        ArgumentCaptor<OffsetDateTime> cutoff = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(repository).deleteCreatedBefore(cutoff.capture());
        assertThat(cutoff.getValue())
                .isCloseTo(OffsetDateTime.now().minusHours(12), within(1, ChronoUnit.MINUTES));
    }

}
