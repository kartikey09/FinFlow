package io.finflow.saga.api;

import io.finflow.saga.model.Vendor;

/**
 * Body for {@code POST /sagas/rebalance}.
 *
 * <p>Day 20 adds {@code vendor}. The value is what routes commands:
 * {@code AWS} → topic {@code saga.commands.aws} → aws-adapter-worker;
 * {@code GCP} → {@code saga.commands.gcp} → gcp-adapter-worker. It is optional
 * on the wire — the controller defaults a missing vendor to {@code AWS} to
 * preserve backward compatibility with existing test scripts.
 */
public record RebalanceRequest(String correlationId, Vendor vendor) {}
