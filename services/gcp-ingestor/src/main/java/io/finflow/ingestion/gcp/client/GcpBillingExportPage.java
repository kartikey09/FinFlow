package io.finflow.ingestion.gcp.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * One page of the Chaos API's GCP billing-export response.
 *
 * <p>Field names match the producer's JSON: {@code rows} and {@code nextPageToken}
 * (GCP's pagination idiom — note it's "nextPageToken" here, where AWS used
 * "nextToken"). The token is omitted on the last page, so it deserializes to
 * {@code null}, which the poll loop reads as "end of export".
 */

@JsonIgnoreProperties(ignoreUnknown = true)
public record GcpBillingExportPage(
        List<GcpBillingRow> rows,
        String nextPageToken
) {}