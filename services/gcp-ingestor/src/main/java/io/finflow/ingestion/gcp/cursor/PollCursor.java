package io.finflow.ingestion.gcp.cursor;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * The resumable poll position for a GCP billing source (e.g. "gcp-billing").
 * Maps to gcp_ingestion.poll_cursor.
 *
 * <p>Deliberately a separate copy from aws-ingestor's PollCursor, in this
 * service's OWN schema: two services can't share one Flyway-managed schema (their
 * histories would collide), and services don't depend on each other. Small,
 * owned-here duplication is the right call.
 */

@Entity
@Table(name = "poll_cursor", schema = "gcp_ingestion")
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

    protected PollCursor() {
        /* for JPA */
    }

    public PollCursor(String source) {
        this.source = source;
    }

    public String getSource() {
        return source;
    }
    public String getLastToken() {
        return lastToken;
    }
    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

}