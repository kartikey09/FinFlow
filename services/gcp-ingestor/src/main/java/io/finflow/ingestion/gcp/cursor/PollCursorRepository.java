package io.finflow.ingestion.gcp.cursor;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PollCursorRepository extends JpaRepository<PollCursor, String> {
}