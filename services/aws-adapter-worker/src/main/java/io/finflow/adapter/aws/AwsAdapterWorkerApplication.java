package io.finflow.adapter.aws;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The aws-adapter-worker — the first place saga commands actually DO something.
 *
 * <p>Reads {@code saga.commands.aws}, calls the Chaos API's rebalance
 * endpoints, publishes results to {@code saga.events} via the outbox. Stateless
 * except for a small dedup table.
 *
 * <p>The full loop, in one line: <b>orchestrator outbox → Kafka → adapter
 * consumer → Chaos API call → adapter outbox → Kafka → orchestrator consumer.</b>
 *
 * <p>Deliberately UNAWARE of the saga state machine. It doesn't know what
 * ACQUIRE_LOCK means philosophically — it just knows the endpoint to call and
 * how to report back. That's the point of the adapter pattern: swap the
 * chaos-api URL for a real AWS control-plane URL and the code doesn't change.
 */
@SpringBootApplication
public class AwsAdapterWorkerApplication {
    public static void main(String[] args) {
        SpringApplication.run(AwsAdapterWorkerApplication.class, args);
    }
}
