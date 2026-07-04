package io.finflow.chaosapi.gcp.dto;

/**
 * GCP mirror of the AWS commitment action response — same shape, own class
 * so the two vendor packages don't share DTOs (matches the ingestor pattern
 * where each vendor has its own DTOs).
 */
public record CommitmentActionResponse(
        boolean success,
        String commitmentId,
        String action
) {}
