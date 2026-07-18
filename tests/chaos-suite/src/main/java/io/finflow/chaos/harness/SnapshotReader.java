package io.finflow.chaos.harness;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.finflow.chaos.harness.SystemSnapshot.CommandCounts;
import io.finflow.chaos.harness.SystemSnapshot.SagaRecord;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Reads a {@link SystemSnapshot} straight out of Postgres.
 *
 * <p>All the SQL in the suite lives here and nowhere else. {@link Invariants} stays
 * a pure function of the snapshot, which is what makes it unit-testable without a
 * database.
 *
 * <p>We read the DB rather than the services' REST APIs on purpose: half of these
 * scenarios SIGKILL a service, so its API is gone at exactly the moment we need to
 * know what it did.
 */
public final class SnapshotReader implements AutoCloseable {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final Connection conn;
    private final KafkaLag kafkaLag;

    public SnapshotReader(String jdbcUrl, String user, String password, KafkaLag kafkaLag) throws SQLException {
        this.conn = DriverManager.getConnection(jdbcUrl, user, password);
        this.kafkaLag = kafkaLag;
    }

    /** @param correlationPrefix only sagas whose correlation_id starts with this are read. */
    public SystemSnapshot read(String correlationPrefix) throws SQLException {
        Map<UUID, SagaRecord> sagas = readSagas(correlationPrefix);
        return new SystemSnapshot(
                sagas,
                readCommandCounts(),
                scalar("SELECT COUNT(*) FROM cost_ledger"),
                scalar("SELECT COUNT(DISTINCT (tenant_id, event_id)) FROM cost_ledger"),
                scalar("SELECT COUNT(*) FROM outbox_event WHERE aggregate_type = 'billing'"),
                readReplicationSlotLag(),
                kafkaLag.byConsumerGroup());
    }

    private Map<UUID, SagaRecord> readSagas(String correlationPrefix) throws SQLException {
        Map<UUID, SagaRecord> out = new LinkedHashMap<>();
        String sql = """
                SELECT id, correlation_id, current_state, completed_steps::text AS steps
                FROM saga.saga_instances
                WHERE correlation_id LIKE ?
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, correlationPrefix + "%");
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UUID id = (UUID) rs.getObject("id");
                    out.put(id, new SagaRecord(
                            id,
                            rs.getString("correlation_id"),
                            rs.getString("current_state"),
                            parseSteps(rs.getString("steps"))));
                }
            }
        }
        return out;
    }

    private static List<String> parseSteps(String jsonb) {
        if (jsonb == null || jsonb.isBlank()) return List.of();
        try {
            return JSON.readValue(jsonb, JSON.getTypeFactory()
                    .constructCollectionType(List.class, String.class));
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * DO / UNDO counts per saga, across BOTH adapter schemas.
     *
     * <p>doSuccessCount is split out from doCount deliberately — see the note on
     * {@link CommandCounts}. The step that FAILS and triggers compensation still has
     * a processed_command row (outcome='FAILURE'), but it moved no money, so there
     * is nothing to undo for it.
     */
    private Map<UUID, CommandCounts> readCommandCounts() throws SQLException {
        String sql = """
                SELECT saga_id, direction, outcome, COUNT(*) AS n
                FROM (
                    SELECT saga_id, direction, outcome FROM aws_adapter.processed_command
                    UNION ALL
                    SELECT saga_id, direction, outcome FROM gcp_adapter.processed_command
                ) c
                GROUP BY saga_id, direction, outcome
                """;
        Map<UUID, int[]> acc = new HashMap<>();   // [doTotal, doSuccess, undo]
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                UUID sagaId = (UUID) rs.getObject("saga_id");
                String direction = rs.getString("direction");
                String outcome = rs.getString("outcome");
                int n = rs.getInt("n");
                int[] a = acc.computeIfAbsent(sagaId, k -> new int[3]);
                if ("DO".equals(direction)) {
                    a[0] += n;
                    if ("SUCCESS".equals(outcome)) a[1] += n;
                } else if ("UNDO".equals(direction)) {
                    a[2] += n;
                }
            }
        }
        Map<UUID, CommandCounts> out = new HashMap<>();
        acc.forEach((id, a) -> out.put(id, new CommandCounts(a[0], a[1], a[2])));
        return out;
    }

    /**
     * How far behind Debezium is, in WAL bytes, for each replication slot.
     *
     * <p>THIS is the honest "has the outbox drained?" question. Counting rows in
     * outbox_event answers a different question entirely (see Invariants.pipelineDrained).
     *
     * <p>confirmed_flush_lsn is NULL for a slot that has never been consumed, so we
     * fall back to restart_lsn rather than blowing up.
     */
    private Map<String, Long> readReplicationSlotLag() throws SQLException {
        String sql = """
                SELECT slot_name,
                       pg_wal_lsn_diff(
                           pg_current_wal_lsn(),
                           COALESCE(confirmed_flush_lsn, restart_lsn)
                       )::bigint AS lag_bytes
                FROM pg_replication_slots
                WHERE slot_name LIKE 'finflow%'
                """;
        Map<String, Long> out = new LinkedHashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.put(rs.getString("slot_name"), rs.getLong("lag_bytes"));
            }
        }
        return out;
    }

    private long scalar(String sql) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0L;
        }
    }

    /** Sagas started by this run, for the expected-set. */
    public List<UUID> sagaIdsFor(String correlationPrefix) throws SQLException {
        List<UUID> ids = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id FROM saga.saga_instances WHERE correlation_id LIKE ?")) {
            ps.setString(1, correlationPrefix + "%");
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) ids.add((UUID) rs.getObject("id"));
            }
        }
        return ids;
    }

    @Override
    public void close() throws SQLException {
        conn.close();
    }
}
