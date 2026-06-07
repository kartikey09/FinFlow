package io.finflow.chaosapi.gcp.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * The response envelope for GET /gcp/billing-export.
 *
 * Real GCP billing export is queried from a BigQuery table, but for a pollable
 * HTTP mock we wrap the rows in a paginated response — the same nextPageToken
 * idiom the BigQuery/GCP APIs use. The gcp-ingestor follows nextPageToken
 * until it's null, exactly as it would page a real GCP API response.
 *
 * Note the field name: GCP conventionally calls this "nextPageToken", whereas
 * AWS called it "nextToken". Keeping the vendor-accurate names is part of the
 * fidelity that lets each ingestor be written the way the real one would be.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GcpBillingExportResponse(
        List<GcpBillingRow> rows,
        String nextPageToken   // null on the final page
) {}
