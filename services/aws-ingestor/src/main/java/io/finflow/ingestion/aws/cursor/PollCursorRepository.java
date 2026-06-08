package io.finflow.ingestion.aws.cursor;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository over the poll cursors. Day 11's poll loop will read
 * the last token here before each poll and save the new one after a page is
 * fully published to Kafka.
 */
public interface PollCursorRepository extends JpaRepository<PollCursor, String> {
}
