package io.finflow.query.projection.commitment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommitmentHealthRepository extends JpaRepository<CommitmentHealth, CommitmentHealthKey> {

    /** Backing query for GET /api/v1/commitments?status=... */
    List<CommitmentHealth> findByTenantIdAndStatus(String tenantId, String status);

    /** Backing query for GET /api/v1/commitments (no status filter). */
    List<CommitmentHealth> findByTenantIdOrderByLatestFractionAsc(String tenantId);
}
