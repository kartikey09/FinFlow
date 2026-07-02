package io.finflow.saga.command;

import io.finflow.saga.model.SagaStep;
import org.springframework.stereotype.Component;

/**
 * Decides which vendor adapter is responsible for a given step.
 *
 * <p>For Day 17 we assume every saga is an AWS rebalance (the demo case). The
 * REST endpoint on Day 20 will let the user pick target/source, and the
 * responsibility mapping might become richer — but this shape keeps that
 * change local.
 *
 * <p>Returned as an {@code aggregate_type} string because that's what the
 * outbox Event Router routes on. It is INTENTIONALLY the full downstream topic
 * name ({@code saga.commands.aws}, {@code saga.commands.gcp}) — the saga
 * connector (see {@code infra/debezium/saga-outbox-connector.json}) uses an
 * identity replacement, so what you set here is what the topic is called.
 */
@Component
public class CommandTopicMap {

    /** For Day 17 the rebalance saga's commands all go to AWS. Day 20 will parameterize this. */
    public String aggregateTypeFor(SagaStep step) {
        return "saga.commands.aws";
    }
}
