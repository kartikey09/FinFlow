package io.finflow.ingestion.aws;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The AWS ingestor. On Day 5 this is a SHELL: it boots, connects to Postgres
 * and Kafka, runs its Flyway migration, and exposes health — proving the
 * plumbing the chaos-api never exercised (it had no DB and no Kafka).
 *
 * The actual poll loop (poll chaos-api's CUR endpoint → publish raw billing
 * events to Kafka, advancing the poll_cursor) lands on Day 11.
 *
 * Component scan roots at io.finflow.ingestion.aws, covering config/, cursor/,
 * health/, and smoke/. platform-common (io.finflow.common) is intentionally
 * outside this tree — it holds only constants/enums, no beans to scan.
 */
@SpringBootApplication
public class AwsIngestorApplication {
    public static void main(String[] args) {
        SpringApplication.run(AwsIngestorApplication.class, args);
    }
}
