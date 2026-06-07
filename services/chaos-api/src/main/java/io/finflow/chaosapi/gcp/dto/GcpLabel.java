package io.finflow.chaosapi.gcp.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One entry of the "labels" array, e.g. { "key": "team", "value": "platform" }.
 *
 * This is GCP's analogue of AWS's "resourceTags/user:Team". The structural
 * difference is sharp: AWS flattens tags into prefixed columns, while GCP keeps
 * them as an array of {key,value} objects. The normalizer has to walk this
 * array looking for the "team" key — it can't just read a column.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GcpLabel(
        String key,
        String value
) {}
