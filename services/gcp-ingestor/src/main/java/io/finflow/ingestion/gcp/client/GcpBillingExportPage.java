package io.finflow.ingestion.gcp.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GcpBillingExportPage(
        List<GcpBillingRow> rows,
        String nextPageToken
) {}