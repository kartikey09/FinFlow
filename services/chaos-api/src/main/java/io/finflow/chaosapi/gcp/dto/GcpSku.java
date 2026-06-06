package io.finflow.chaosapi.gcp.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The "sku" sub-object, e.g.
 * { "id": "D0AA-3C5D-...", "description": "A2 Instance Core running in Americas" }.
 *
 * The SKU description is GCP's closest analogue to AWS's instance-type string —
 * it's where the gcp-ingestor will dig out which machine family was used.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GcpSku(
        String id,
        String description
) {}
