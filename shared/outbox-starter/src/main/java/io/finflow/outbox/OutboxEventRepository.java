package io.finflow.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * What this module does:
 * This is the Spring Data JPA repository for the `outbox_event` table. It has
 * exactly two jobs: saving new events (handled automatically by Spring), and
 * deleting old events.
 *
 * What it DOES NOT do (The Interview Flex):
 * Notice that there is NO method here called `findByStatus("PENDING")`.
 * In a beginner's architecture, a background thread wakes up every 5 seconds,
 * queries the database for pending events, and sends them to Kafka. That is
 * called "Polling," and it crushes database performance at scale.
 * * We are using Debezium (CDC). Debezium reads the PostgreSQL Write-Ahead Log
 * (WAL) directly from the hard drive. It never runs SQL queries against this
 * table. Because Debezium handles the reading, this repository only exists
 * for writing and cleanup.
 *
 * Why we built the cleanup method:
 * Because Debezium reads the WAL, the actual row sitting in the `outbox_event`
 * table is completely useless the millisecond after it is saved. If we don't
 * delete them, this table will grow to billions of rows and consume the entire
 * hard drive. This repository provides the broom to sweep them away.
 */

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    // By default, Spring assumes custom @Query methods are SELECT statements.
    // @Modifying tells Spring and Hibernate: "Warning, this query will alter
    // the database. Skip the read-only optimizations and execute an update/delete."
    @Modifying
    // This custom query fires exactly ONE command to the database:
    // "DELETE FROM outbox_event WHERE created_at < X". It is lightning fast.
    // instead of a bulk query where we would have found all the trans. created before that time and deleted 1 by 1
    @Query("delete from OutboxEvent e where e.createdAt < :cutoff")
    int deleteCreatedBefore(@Param("cutoff") OffsetDateTime cutoff);
    // returns an int which tells the no. of queries succ. deleted.
}