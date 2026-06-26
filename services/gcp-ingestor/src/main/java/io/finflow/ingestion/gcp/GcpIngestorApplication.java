package io.finflow.ingestion.gcp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The GCP ingestor — the symmetric twin of aws-ingestor.
 *
 * <p>Same shape as Day 11 (poll the Chaos API on a schedule, land each row, emit
 * a RawCostLineItem through the outbox, advance a poll cursor), pointed at GCP's
 * billing-export endpoint instead of AWS CUR. The architecture earns its keep
 * here: the outbox-starter, the CDC pipeline, and the poll/land/emit pattern are
 * all reused, so the only genuinely new code is GCP's nested parsing — walking
 * the credits[] array for committed-use discounts and converting cost to USD.
 *
 * <p>Component scan roots at io.finflow.ingestion.gcp (config/, cursor/, client/,
 * ingest/). The shared outbox lives under io.finflow.outbox and is brought in
 * explicitly by JpaConfig.
 */
@SpringBootApplication
public class GcpIngestorApplication {
    public static void main(String[] args) {
        SpringApplication.run(GcpIngestorApplication.class, args);
    }
}
