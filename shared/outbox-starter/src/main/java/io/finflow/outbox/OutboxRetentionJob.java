package io.finflow.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;

/**
 * What this module does:
 * This class is an automated background worker. On a fixed schedule (usually
 * the middle of the night), it wakes up, calculates a "cutoff date" (e.g.,
 * 7 days ago), and tells the database to delete any outbox events older than
 * that date.
 *
 * Why we built it (The "Append-Only" Problem):
 * Because we chose Debezium (CDC) instead of polling, Debezium reads the
 * PostgreSQL WAL file and never executes a `DELETE` statement. That means
 * our `outbox_event` table is an "append-only" table. It grows forever.
 * If your system processes 100,000 billing events a day, you will have
 * 36.5 million useless rows sitting in your database after a year. This
 * job prevents your database hard drive from hitting 100% capacity.
 *
 * Why it is completely safe:
 * Debezium captures the database changes from the WAL log within milliseconds.
 * By the time an event is 7 days old, it has long been safely pushed to Kafka.
 * Deleting the row from Postgres has absolutely zero impact on the downstream
 * Kafka consumers. It is purely local housekeeping.
 */

public class OutboxRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(OutboxRetentionJob.class);

    // The repository to execute the delete query, and a Duration to define
    // how long we keep records (e.g., 7 days).
    private final OutboxEventRepository repository;
    private final Duration retention;

    // Constructor Injection - We inject the repository and the retention
    // configuration when this bean is created.
    public OutboxRetentionJob(OutboxEventRepository repository, Duration retention){
        this.repository = repository;
        this.retention = retention;
    }

    // @Scheduled -
    // This tells Spring's task scheduler to execute this method automatically.
    // The cron expression translates to: "Run at 03:00 AM every single day."
    // property placeholder `${...:0 0 3 * * *}`. This means we can override
    // the schedule in our `application.yml`, but if we don't, it defaults to 3 AM.
    @Scheduled(cron = "${finflow.outbox.retention.cron:0 0 3 * * *}")
    // Spring Data requires all `@Modifying` queries to be executed inside a
    // transaction. This ensures the bulk delete locks the necessary rows safely.
    // and we have used @Modifying in OutboxEventRepository.java query
    @Transactional
    public void purgeExpiredEvents(){
        // calculating the exact cutoff time-  now() - minus retention duration
        OffsetDateTime cutoff = OffsetDateTime.now().minus(retention);
        // Call the highly optimized, single-query bulk delete we wrote in the
        // repository, and capture the exact number of rows it deleted.
        int purged = repository.deleteCreatedBefore(cutoff);

        // Based on the no. of rows deleted log into respective log tables
        if(purged>0){
            log.info("Outbox retention: purged {} event(s) created before {}", purged, cutoff);
        }
        else{
            log.debug("Outbox retention: nothing to purge before {}", cutoff);
        }
    }
}
