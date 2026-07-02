package io.finflow.query;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The query-api — the read side of the CQRS split, and the backend for the
 * dashboard.
 *
 * <p>Two responsibilities:
 * <ol>
 *   <li>Consume {@code finflow.events.cost-normalized} and
 *       {@code finflow.events.commitment} into projection tables optimized for
 *       reads (denormalized, pre-aggregated, indexed for the exact queries the
 *       UI runs).</li>
 *   <li>Serve those tables via a thin REST API the React dashboard hits.</li>
 * </ol>
 *
 * <p>Unlike every other service in Week 3, this one is NOT an outbox producer —
 * it's a pure sink. That's the point of CQRS: the write path (Days 11–14) owns
 * the event-sourced truth; the read path owns denormalized shapes for fast UI.
 */
@SpringBootApplication
public class QueryApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(QueryApiApplication.class, args);
    }
}
