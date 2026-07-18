package io.finflow.recommendation.store;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OndemandSpendRollupRepository
        extends JpaRepository<OndemandSpendRollup, OndemandSpendRollupKey> {
}
