package io.finflow.chaosapi.chaos;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//importing a Spring feature that allows you to hook into the lifecycle of an HTTP request
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.concurrent.ConcurrentHashMap;

/**
 * The "Bouncer" that enforces the chaos rules.
 *
 * What this does:
 * This class physically intercepts every single incoming web request BEFORE it
 * is allowed to reach your controllers. It asks the ChaosDecider what to do
 * with the request. It then either lets it pass, forcibly pauses the server
 * for 5 seconds (HANG), or manually writes a 503 Error and completely cancels
 * the request so the controller never even knows it happened.
 *
 * Why it is built this way:
 * By putting all the failure logic in this one interceptor, we achieve "zero-touch"
 * chaos. We do not have to write messy error-throwing code inside our actual
 * AWS or GCP controllers. The controllers remain perfectly clean, while this
 * interceptor creates a highly hostile network environment around them.
 *
 * <h2>Day 22: finflow.chaos.faults.injected</h2>
 *
 * <p>Every decision increments {@code finflow.chaos.faults.injected} tagged with
 * {@code outcome} (pass|fail_503|hang) and {@code vendor} (aws|gcp|other). This
 * is what the Day-23 Reliability dashboard plots as "chaos fault rate" — and
 * it's the ground truth to correlate against the downstream services'
 * circuit-breaker states and retry counts. Counting PASS too (not just faults)
 * lets the dashboard compute an actual injected-fault RATE, not just a raw count.
 */
//By implementing HandlerInterceptor, officially creating a middleman
public class ChaosInterceptor implements HandlerInterceptor{

    private static final Logger log = LoggerFactory.getLogger(ChaosInterceptor.class);
    private static final String COUNTER_NAME = "finflow.chaos.faults.injected";

    /**
     * What it does: The Interceptor doesn't know when to block people, it just knows how to block people.
     * Here, you inject your ChaosDecider so the Interceptor can ask it what to do on every single request.
     */
    private final ChaosDecider decider;
    private final MeterRegistry meterRegistry;
    private final ConcurrentHashMap<String, Counter> counters = new ConcurrentHashMap<>();

    public ChaosInterceptor(ChaosDecider decider, MeterRegistry meterRegistry){
        this.decider = decider;
        this.meterRegistry = meterRegistry;
    }

    /**
     * What it does:
     * This is the most powerful method in the class. preHandle triggers after the server receives the HTTP request,
     * but before it hands it to your AwsBillingController.
     * The Rule of preHandle: If this method returns true, the request is allowed to continue to the controller.
     * If it returns false, Spring stops dead in its tracks, drops the request, and never runs the controller.
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
        throws Exception {
        String uri = request.getRequestURI();
        String tag = prefixFor(uri); //Grabs the URL the user is trying to reach
        // (e.g., /aws/cost-and-usage-report) and generates a clean logging tag like [AWS-CHAOS] so your terminal output is easy to read.
        String vendor = vendorFor(uri);

        switch(decider.decide(uri)){ //Day 20: URI-aware so a target-path filter can single out one saga step.
            case PASS -> {
                count("pass", vendor);
                return true;  //If the dice roll says PASS, it immediately returns true. The Bouncer steps aside,
                              // and the user reaches the target controller completely unaware they were just evaluated.
            }
            /**
             * What it does: The immediate rejection!
             * It logs a bright yellow warning to your terminal.
             * It hijacks the response object and manually sets the HTTP status to 503 Service Unavailable.
             * It writes a custom JSON error message directly to the output stream.
             * CRITICAL: It returns false. This prevents the AwsBillingController from ever executing.
             */
            case FAIL_503 -> {
                count("fail_503", vendor);
                log.warn("{} INJECTING 503 for {} {}", tag, request.getMethod(), uri);
                response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE); //503
                response.setContentType("application/json");
                response.getWriter().write(
                        "{\"error\":\"service unavailable\",\"injectedBy\":\"chaos-api\"}");
                return false;
            }
            /**
             * What it does: The slow torture.
             * It asks the decider how long the hang should be (default 5000ms).
             * It logs the warning.
             * It calls Thread.sleep(ms), which literally freezes the server thread handling this user for 5 solid seconds
             * The user's loading spinner will just spin.
             * CRITICAL: After 5 seconds, it returns true. The request does eventually go to the controller and succeed.
             * As the comment notes, this is a "slow success" designed to test if the downstream service has a Timeout limit configured.
             */
            case HANG -> {
                count("hang", vendor);
                long ms = decider.hangMillis();
                log.warn("{} INJECTING {}ms HANG for {} {}", tag, ms, request.getMethod(), uri);
                Thread.sleep(ms);
                return true;
            }
        }
        return true;
    }

    private void count(String outcome, String vendor) {
        if (meterRegistry == null) return;
        counters.computeIfAbsent(outcome + "|" + vendor, key ->
                Counter.builder(COUNTER_NAME)
                        .description("Chaos decisions made by the interceptor, by outcome and vendor")
                        .tag("outcome", outcome)
                        .tag("vendor", vendor)
                        .register(meterRegistry)
        ).increment();
    }

    //Logging Helper
    //Provides the "tag" variable
    //A simple string-matching helper to ensure your logs explicitly state which vendor API is currently being sabotaged.
    private String prefixFor(String uri){
        if(uri.startsWith("/gcp"))
            return "[GCP-CHAOS]";

        if(uri.startsWith("/aws"))
            return "[AWS-CHAOS]";

        return "[CHAOS]";
    }

    private String vendorFor(String uri){
        if(uri.startsWith("/gcp")) return "gcp";
        if(uri.startsWith("/aws")) return "aws";
        return "other";
    }
}
