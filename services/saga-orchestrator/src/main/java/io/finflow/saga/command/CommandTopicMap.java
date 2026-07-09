package io.finflow.saga.command;

import io.finflow.saga.model.SagaStep;
import io.finflow.saga.model.Vendor;
import org.springframework.stereotype.Component;

/**
 * Picks the outbound topic (via the outbox {@code aggregate_type}) for one command.
 *
 * <p>Day 17 hard-coded {@code saga.commands.aws}. Day 20 threads the vendor
 * through so the two adapter workers can share the road. The mapping is
 * intentionally trivial today — one topic per vendor, regardless of step. If
 * we ever need per-step routing (e.g. UPDATE_LEDGER always goes to a special
 * "reconciliation worker"), this is the one place to add it.
 *
 * <p>Returned as the outbox {@code aggregate_type} because that's what the
 * saga Debezium connector routes on. The saga connector uses identity
 * replacement ({@code route.topic.replacement = ${routedByValue}}), so what
 * we set here is the actual Kafka topic name.
 */
@Component
public class CommandTopicMap {

    public String aggregateTypeFor(SagaStep step, Vendor vendor) {
        if (vendor == null) {
            // Should never happen for Day-20-or-later sagas; guard defensively so a
            // null doesn't silently emit "saga.commands.null".
            throw new IllegalStateException(
                    "vendor is required to route command for step " + step);
        }
        return switch (vendor) {
            case AWS -> "saga.commands.aws";
            case GCP -> "saga.commands.gcp";
        };
    }
}
