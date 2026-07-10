package io.finflow.ingestion.aws.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

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
 *
 * <h2>Day 21 change: full HTTP resilience stack</h2>
 *
 * <p>Day 11 shipped just {@code @Retry}. Day 21 audits every external HTTP call
 * and applies the plan's full stack: Retry + CircuitBreaker + TimeLimiter. The
 * TimeLimiter forces a {@code CompletableFuture} return (that's how Resilience4j
 * enforces a hard deadline), so {@code fetchPage} now returns
 * {@code CompletableFuture<CostAndUsageReportPage>}. The only caller, the
 * scheduler's {@code poll()}, blocks on it with {@code .get()} and its existing
 * try/catch handles the {@code ExecutionException}. The async pattern doesn't
 * cascade beyond that one method.
 *
 * <p>All three annotations use the instance name {@code "chaos-api"}, unchanged
 * from Day 11, so the config in application.yml keeps working and the actuator
 * event endpoints report under the same key.
 */
@Component
public class AwsCurClient {

    // A small daemon executor for the TimeLimiter's CompletableFuture. The poll
    // loop is single-threaded (one @Scheduled tick at a time), so this pool
    // rarely holds more than one active thread.
    private static final Executor CHAOS_EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "aws-cur-timelimiter");
        t.setDaemon(true);
        return t;
    });

    // sync. HTTP client - RestClient is Spring Boot 3's modern, fluent API for making HTTP calls
    // (replacing the older RestTemplate)
    private final RestClient restClient;

    public AwsCurClient(RestClient chaosApiRestClient){
        this.restClient = chaosApiRestClient;
    }

    // This tells Resilience4j to intercept calls to this method.
    // The name "chaos-api" links to the application.yml configuration
    // (3 attempts, 200ms base + jitter; CB 50%/20; TimeLimiter 7s).
    // (AOP Proxying)-
    // For the annotations to work, Spring wraps this class in an invisible
    // "Proxy". Therefore, this method MUST be called from a different class
    // (the scheduler), which it is.
    @Retry(name = "chaos-api")
    @CircuitBreaker(name = "chaos-api")
    @TimeLimiter(name = "chaos-api")
    /// pagination token to get data in chunks
    public CompletableFuture<CostAndUsageReportPage> fetchPage(String nextToken){
        return CompletableFuture.supplyAsync(() ->
                restClient.get()
                        // dynamically builds the URI
                        .uri(uriBuilder -> {
                            uriBuilder.path("/aws/cost-and-usage-report");
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
                        .body(CostAndUsageReportPage.class),
                CHAOS_EXECUTOR);
    }
}
