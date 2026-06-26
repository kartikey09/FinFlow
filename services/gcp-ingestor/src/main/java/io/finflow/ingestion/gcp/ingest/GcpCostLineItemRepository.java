package io.finflow.ingestion.gcp.ingest;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository over the GCP raw landing table. {@code existsById(rowKey)} is the
 * idempotency gate before insert + outbox append.
 */
public interface GcpCostLineItemRawRepository extends JpaRepository<GcpCostLineItemRaw, String> {
}
