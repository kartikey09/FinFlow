package io.finflow.chaosapi.aws.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * The response package (DTO) for the mock AWS Cost and Usage Report API.
 *
 * What this does - Pagination-
 * This record packages a list of "AwsCurLineItem" objects alongside a "nextToken".
 * This simulates standard cloud API pagination, breaking massive billing datasets
 * down into smaller, manageable chunks rather than crashing the system with one giant response.
 *
 * Why the @JsonInclude(NON_NULL) matters - Stop Condition
 * When the API serves the very last page of data, the "nextToken" is set to null in Java.
 * Because of this annotation, Jackson completely strips the "nextToken" key from the
 * resulting JSON output. The downstream ingestor service relies on the absence
 * of this key to know that it has successfully reached the end of the report.
 */

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CostAndUsageReportResponse (
        String billingPeriod,
        List<AwsCurLineItem> lineItems,
        String nextToken
) {}
