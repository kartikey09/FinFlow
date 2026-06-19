package io.finflow.ingestion.aws.client;

import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * What this module does-
 * This class is the actual HTTP client that reaches out across the network to
 * download AWS Cost and Usage Report (CUR) data from our Chaos API.
 *
 * Why we built it-
 * Network calls in distributed systems are inherently unreliable. Because our
 * Chaos API intentionally throws 503 errors 20% of the time, a naive HTTP
 * request would crash the ingestor. Instead of writing messy try-catch blocks
 * everywhere, we wrap this client in Resilience4j. It acts as a shield,
 * automatically absorbing transient network failures and retrying the request
 * before the rest of the application even knows something went wrong.
 */

@Component
public class AwsCurClient {
    // sync. HTTP client - RestClient is Spring Boot 3's modern, fluent API for making HTTP calls
    // (replacing the older RestTemplate)
    private final RestClient restClient;


    public AwsCurClient(RestClient chaosApiRestClient){
        this.restClient = chaosApiRestClient;
    }

    // This tells Resilience4j to intercept calls to this method.
    // The name "chaos-api" links to the application.yml configuration (e.g.,
    // 3 max attempts, 1 second backoff).
    // (AOP Proxying)-
    // For @Retry to work, Spring wraps this class in an invisible "Proxy".
    // Therefore, this method MUST be called from a completely different class
    @Retry(name = "chaos-api")
    /// pagination token to get data in chunks
    public CostAndUsageReportPage fetchPage(String nextToken){
        return restClient.get()
                // dynamically builds the URI
                .uri(uriBuilder -> {
                    uriBuilder.path("/aws/cost-and-uasge-report");
                    // if we have a token(not on the first page) then append it in the query as param
                    if (nextToken != null && !nextToken.isBlank()){
                        uriBuilder.queryParam("nextToken", nextToken);
                    }
                    return uriBuilder.build();
                })
                // execute the HTTP GET
                .retrieve()
                // Automatically deserialize the Chaos API's JSON response body
                // directly into our CostAndUsageReportPage Java object.
                .body(CostAndUsageReportPage.class);
    }
}
