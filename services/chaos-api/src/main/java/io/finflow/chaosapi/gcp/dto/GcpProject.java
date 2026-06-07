package io.finflow.chaosapi.gcp.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The "project" sub-object, e.g.
 * { "id": "northwind-platform", "name": "Northwind Platform" }.
 *
 * A GCP project is the rough equivalent of an AWS account — it's the unit the
 * normalizer keys spend on. In the canonical model (Week 3) both an AWS
 * usageAccountId and a GCP project.id collapse to the same "account" concept.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GcpProject(
        String id,
        String name
) {}
