package io.finflow.chaosapi.aws.dto;

/**
 * The response payload (DTO) for a successful AWS EC2 Reserved Instance purchase.
 *
 * What this does - Response to the purchase request
 * After the API successfully processes a purchase request, it returns this object
 * containing a freshly generated mock AWS Reservation ID and a status string.
 *
 * Why it exists - The Saga & Fault Injection
 * This provides the successful confirmation for the FinFlow rebalance saga.
 * Later in the project (Day 4), fault injection will be added to this endpoint to
 * randomly fail the request, to test the saga's automatic
 * rollback/compensation mechanisms in a simulated crisis.
 */

public record PurchaseReservedInstancesResponse(
        String reservationId,
        String state
) {}
