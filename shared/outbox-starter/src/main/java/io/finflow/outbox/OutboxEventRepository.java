package io.finflow.outbox;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Spring Data repository over the outbox. Mostly the appender saves through
 * this; a retention job (later) might query/prune old rows. Debezium reads the
 * WAL, not this repository, so there is no "publish" query here by design.
 */
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
}