package io.finflow.commitment.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;

public interface CommitmentRepository extends JpaRepository<Commitment, String> {

    /** Used by the expiry checker: every commitment whose {@code expires_at} falls in (now, threshold]. */
    List<Commitment> findByExpiresAtBetween(OffsetDateTime from, OffsetDateTime to);
}
