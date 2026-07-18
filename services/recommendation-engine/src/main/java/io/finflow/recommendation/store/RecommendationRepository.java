package io.finflow.recommendation.store;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecommendationRepository extends JpaRepository<Recommendation, UUID> {

    /** The natural-key lookup used to decide UPDATE-vs-INSERT for commitment-keyed recos. */
    Optional<Recommendation> findByTenantIdAndTypeAndCommitmentId(
            String tenantId, String type, String commitmentId);

    /** PURCHASE_COMMITMENT has a null commitment_id; it is keyed on (account, service) instead. */
    Optional<Recommendation> findByTenantIdAndTypeAndAccountIdAndService(
            String tenantId, String type, String accountId, String service);

    /** All OPEN recommendations for a commitment — used when a new type supersedes an old one. */
    List<Recommendation> findByTenantIdAndCommitmentIdAndStatus(
            String tenantId, String commitmentId, String status);

    /** The dashboard's "top recommendations": highest estimated savings first. */
    @Query("""
            SELECT r FROM Recommendation r
            WHERE r.status = 'OPEN'
            ORDER BY r.estimatedSavingsUsd DESC
            """)
    List<Recommendation> findTopOpen();
}
