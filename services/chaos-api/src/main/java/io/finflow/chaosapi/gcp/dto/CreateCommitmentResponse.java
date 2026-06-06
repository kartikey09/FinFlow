package io.finflow.chaosapi.gcp.dto;

/**
 * Response for a successful GCP Committed Use Discount creation.
 *
 * Returns a fully-qualified commitment name (GCP's resource-name convention,
 * e.g. "projects/northwind-platform/regions/us-central1/commitments/cud-...")
 * and a status. On Day 3 this endpoint always succeeds; Day 4 adds the fault
 * injection that gives the saga's compensation path real failures to react to.
 */
public record CreateCommitmentResponse(
        String commitmentName,
        String status
) {}
