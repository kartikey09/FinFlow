package io.finflow.chaosapi.gcp.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The "service" sub-object of a GCP billing row, e.g.
 * { "id": "6F81-5844-456A", "description": "Compute Engine" }.
 *
 * In AWS this was a flat "lineItem/ProductCode" string. In GCP it's a nested
 * object with both a stable id and a human description.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GcpService(
        String id,
        String description
) {}
