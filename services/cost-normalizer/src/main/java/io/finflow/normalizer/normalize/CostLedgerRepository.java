package io.finflow.normalizer.normalize;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CostLedgerRepository extends JpaRepository<CostLedgerEntry, Long> {
}
