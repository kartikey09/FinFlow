package io.finflow.commitment.expiry;

import io.finflow.commitment.domain.Commitment;
import io.finflow.commitment.domain.CommitmentRepository;
import io.finflow.outbox.OutboxAppender;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExpiryCheckerTest {

    private final CommitmentRepository repository = mock(CommitmentRepository.class);
    private final OutboxAppender outbox = mock(OutboxAppender.class);
    private final ExpiryChecker checker = new ExpiryChecker(repository, outbox, 30, "default");

    @Test
    void emitsOneEventPerExpiringCommitment() {
        Commitment a = commitment("c-a", OffsetDateTime.now().plusDays(5));
        Commitment b = commitment("c-b", OffsetDateTime.now().plusDays(20));
        when(repository.findByExpiresAtBetween(any(), any())).thenReturn(List.of(a, b));

        checker.emitExpiringSoonEvents();

        verify(outbox).append(eq("commitment"), eq("c-a"), eq("CommitmentExpiringSoon"), any());
        verify(outbox).append(eq("commitment"), eq("c-b"), eq("CommitmentExpiringSoon"), any());
        verify(outbox, times(2)).append(any(), any(), any(), any());
    }

    @Test
    void emitsNothingWhenNothingExpiring() {
        when(repository.findByExpiresAtBetween(any(), any())).thenReturn(List.of());

        checker.emitExpiringSoonEvents();

        verify(outbox, never()).append(any(), any(), any(), any());
    }

    private static Commitment commitment(String id, OffsetDateTime expiresAt) {
        Commitment c = mock(Commitment.class);
        when(c.getCommitmentId()).thenReturn(id);
        when(c.getVendor()).thenReturn("AWS");
        when(c.getAccountId()).thenReturn("acct-1");
        when(c.getExpiresAt()).thenReturn(expiresAt);
        return c;
    }
}
