package io.finflow.saga.api;

/**
 * Body for {@code POST /sagas/rebalance}.
 *
 * <p>Just {@code correlationId} for now. Real business context (source and
 * target commitment ids) will be added on Day 20 when the dashboard button
 * is wired; the orchestrator will store it on the SagaInstance so adapter
 * workers can read it.
 */
public record RebalanceRequest(String correlationId) {}
