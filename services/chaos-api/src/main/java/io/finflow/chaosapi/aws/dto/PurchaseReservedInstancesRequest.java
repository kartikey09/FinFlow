package io.finflow.chaosapi.aws.dto;

/**
 * The request payload (DTO) for the mock AWS EC2 "Purchase Reserved Instances" API.
 *
 * This record defines the exact JSON shape the API expects to receive when someone makes a
 * POST request to /aws/ec2/purchase-reserved-instances.
 * 2 pieces of information needed for a purchase: the specific AWS offering ID and quantity.
 *
 * Why it's structured this way (The Saga & Auto-Mapping):
 * The automated "rebalance saga" will hit this endpoint to simulate making real
 * financial commitments without actually spending the company's money.
 * Why @JsonProperty annotations were not needed - because these Java variable names
 * match the incoming JSON keys, Spring's Jackson parser can automatically and
 * silently map the data right into this immutable record. They do not contain slash or colon.
 */

public record PurchaseReservedInstancesRequest(
        String reservedInstancesOfferingId,
        Integer instanceCount
) {}
