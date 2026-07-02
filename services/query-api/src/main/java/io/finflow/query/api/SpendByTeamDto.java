package io.finflow.query.api;

/**
 * Wire shape for the spend-by-team bar chart. Two fields on purpose —
 * team + cost — because that's all the chart needs. Adding more here couples
 * the API to specific future UI plans; wait for the demand.
 */
public record SpendByTeamDto(String team, double costUsd) {}
