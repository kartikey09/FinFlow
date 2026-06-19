package io.finflow.ingestion.aws.ingest;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * What this module does:
 * This is the standard Spring Data JPA interface used to write our raw AWS
 * billing rows into the local PostgreSQL database.
 *
 * Why is it completely empty?
 * Because Spring Data JPA handles the heavy lifting. By simply extending
 * `JpaRepository<CostLineItemRaw, String>`, Spring automatically generates all
 * the underlying SQL for standard operations like `save()`, `findById()`, and
 * `existsById()` in the background. You don't have to write any boilerplate.
 *
 * The Senior Concept: "Idempotency"
 * The comment mentions using `existsById` as an "idempotency check."
 * In distributed systems, you must assume data will be sent twice. For example,
 * if the `AwsIngestScheduler` downloads a page of 1,000 rows, but the database
 * crashes on row 999, the `nextToken` (cursor) is never updated.
 * The next time the scheduler runs, it will download that EXACT same page of
 * 1,000 rows again.
 * * If we just blindly saved them, we would double-charge the customer.
 * By calling `repository.existsById(lineItemId)` before inserting, the ingestor
 * says: "Have I already saved this exact billing row?" If yes, it safely skips it.
 * This makes the pipeline "idempotent"—meaning you can run the same data
 * through it 100 times, and the final database state will perfectly match
 * running it just 1 time.
 */

// CostLineItemRaw: The Java Entity class representing the database table.
// String: The data type of that Entity's Primary Key (the AWS LineItemId).
public interface CostLineItemRawRepository extends JpaRepository<CostLineItemRaw, String>{
}
