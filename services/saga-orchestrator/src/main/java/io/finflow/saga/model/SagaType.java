package io.finflow.saga.model;

/**
 * Which kind of saga this is. Only one variant today; the enum shape lets us
 * add types later (e.g. {@code MIGRATE}) without a table migration — the
 * {@code type} column already stores strings.
 */
public enum SagaType {
    REBALANCE
}
