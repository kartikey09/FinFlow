package io.finflow.adapter.aws.dedup;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedCommandRepository extends JpaRepository<ProcessedCommand, String> {
}
