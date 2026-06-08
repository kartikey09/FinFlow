package io.finflow.ingestion.aws.cursor;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * The resumable poll position for one billing source (e.g. "aws-cur").
 * Maps to ingestion.poll_cursor (created by V1__baseline.sql).
 *
 * A mutable JPA entity — one of the few places a plain class beats a record,
 * since JPA needs a no-arg constructor and mutable fields.
 */
@Entity
@Table(name = "poll_cursor", schema = "ingestion")
public class PollCursor {

    @Id
    @Column(length = 64)
    private String source;

    @Setter
    @Column(name = "last_token")
    private String lastToken;

    @Setter
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    protected PollCursor() { /* for JPA */ }

    public PollCursor(String source) {
        this.source = source;
    }

    public String getSource()                 { return source; }
    public String getLastToken()              { return lastToken; }
    public OffsetDateTime getUpdatedAt()      { return updatedAt; }

}
