package io.finflow.chaosapi.gcp.dto;

/**
 * Request body for POST /gcp/billing/commitments.
 *
 * Mirrors the shape of GCP's real Committed Use Discount (CUD) purchase, the
 * GCP analogue of buying an AWS Reserved Instance. The gcp-adapter-worker
 * (Week 4) sends this when a rebalance saga reaches its "reserve" step on the
 * GCP side.
 *
 * Where AWS spoke of an "offering id" and an "instance count", GCP speaks of a
 * plan (TWELVE_MONTH / THIRTY_SIX_MONTH), a region, and a resource amount with
 * a unit (e.g. 32 vCPU). The shape difference is deliberate — the two adapter
 * workers send genuinely different payloads, just as they would against the
 * real clouds.
 */
public record CreateCommitmentRequest(
        String projectId,
        String region,
        String plan,
        String resourceType,
        Integer amount
) {}
