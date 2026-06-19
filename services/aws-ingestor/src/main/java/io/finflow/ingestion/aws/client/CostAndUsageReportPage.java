package io.finflow.ingestion.aws.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

// By creating its own CostAndUsageReportPage record in ingestor, it gets to define exactly the fields it needs
// and ignore the rest equipped with @JsonIgnoreProperties(ignoreUnknown = true)

// @JsonIgnoreProperties(ignoreUnknown = true) is a critical defensive practice.
// It tells Jackson: "If the Chaos API (or real AWS) suddenly adds a new field
// to their JSON tomorrow (like 'estimatedTax'), do NOT crash our application.
// Just ignore the fields we haven't explicitly defined below."
@JsonIgnoreProperties(ignoreUnknown = true)
public record CostAndUsageReportPage (
        // These variable names MUST perfectly match the JSON keys coming from
        // the Chaos API.
        String billingPeriod,
        List<CurLineItem> lineItems,
        String nextToken
) {}

/// nextToken string-
// The pagination cursor. If the API returns a string here, the ingestor
// knows there is another page to fetch. If the JSON omits this field,
// Jackson sets it to `null`, signaling the end of the report.