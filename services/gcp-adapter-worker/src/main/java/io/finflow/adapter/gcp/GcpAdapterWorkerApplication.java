package io.finflow.adapter.gcp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The gcp-adapter-worker — mirror of aws-adapter-worker for GCP commands.
 *
 * <p>Reads {@code saga.commands.gcp}, calls the Chaos API's GCP rebalance
 * endpoints ({@code /gcp/billing/commitments/{id}/...}), publishes results to
 * {@code saga.events} — the SAME topic the AWS adapter uses. Per the plan:
 * "the orchestrator doesn't care which vendor responded; it identifies sagas
 * by correlation ID."
 *
 * <p>Same architecture as Day 18. The only things different from
 * aws-adapter-worker are:
 * <ol>
 *   <li>Kafka topic subscribed ({@code saga.commands.gcp})</li>
 *   <li>Chaos-API base path ({@code /gcp/billing/commitments/})</li>
 *   <li>Postgres schema ({@code gcp_adapter})</li>
 *   <li>Server port (8090)</li>
 *   <li>Resilience4j instance name ({@code gcp-chaos})</li>
 * </ol>
 * Everything else is byte-identical after package rename — the "should feel
 * routine" cue in the plan is deliberate: any surprise here would mean Day 18
 * left something undone.
 */
@SpringBootApplication
public class GcpAdapterWorkerApplication {
    public static void main(String[] args) {
        SpringApplication.run(GcpAdapterWorkerApplication.class, args);
    }
}
