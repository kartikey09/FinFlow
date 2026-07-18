package io.finflow.chaos.harness;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * An immutable read of everything the invariants care about, taken at one instant
 * straight from Postgres + Kafka.
 *
 * <p>Everything here comes from the DATABASE, not from the services' REST APIs.
 * That is deliberate and it is the whole reason this suite can work: half these
 * scenarios SIGKILL a service, so its API is gone. Postgres is the only witness
 * that survives the thing we're testing.
 */
public record SystemSnapshot(

        /** saga_id -> its row in saga.saga_instances. */
        Map<UUID, SagaRecord> sagas,

        /** saga_id -> how many DO / UNDO commands the adapters actually executed. */
        Map<UUID, CommandCounts> commands,

        /** COUNT(*) FROM cost_ledger. */
        long ledgerRows,

        /** COUNT(DISTINCT (tenant_id, event_id)) FROM cost_ledger. */
        long ledgerDistinctEvents,

        /** COUNT(*) FROM outbox_event WHERE aggregate_type = 'billing' — events the ingestors emitted. */
        long billingEventsPublished,

        /** slot_name -> bytes of WAL Debezium has NOT yet confirmed. ~0 == CDC has drained. */
        Map<String, Long> replicationSlotLagBytes,

        /** consumer group -> total un-consumed records across its topics. 0 == consumers caught up. */
        Map<String, Long> consumerGroupLag
) {

    /** One row of saga.saga_instances. */
    public record SagaRecord(UUID id, String correlationId, String currentState, List<String> completedSteps) {

        public boolean isTerminal() {
            return "COMPLETED".equals(currentState)
                    || "COMPENSATED".equals(currentState)
                    || "COMPENSATION_FAILED".equals(currentState);
        }
    }

    /**
     * Rows in {aws,gcp}_adapter.processed_command for one saga.
     *
     * <p>THIS is where "no money double-counted" is actually measurable.
     * idempotency_key is the PRIMARY KEY of that table, so one row == one real
     * side effect against the vendor. If redelivery ever minted a NEW key,
     * doCount would exceed the number of steps and the money would move twice.
     *
     * <p>doCount and doSuccessCount are BOTH needed, and conflating them produces
     * a false failure. A saga that compensates does so because a step FAILED — and
     * the executor records that failure as a processed_command row with
     * outcome='FAILURE'. So a saga that got 2 steps in and then failed the 3rd has
     * 3 DO rows but only 2 successful ones, and compensation correctly undoes only
     * those 2. Pairing UNDO against total DO would flag every single compensated
     * saga as broken.
     *
     *   doCount        -> every execution recorded. Used to detect DOUBLE EXECUTION.
     *   doSuccessCount -> steps that actually moved money. UNDO must pair with THESE.
     */
    public record CommandCounts(int doCount, int doSuccessCount, int undoCount) {
        public static final CommandCounts NONE = new CommandCounts(0, 0, 0);
    }
}
