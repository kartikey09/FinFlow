package io.finflow.chaos.harness;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Everything the chaos scenarios need, wired up: the running infra, the service
 * processes we own, both fault injectors, and the snapshot reader.
 *
 * <p>The suite assumes the INFRASTRUCTURE is already up (docker compose) and that the
 * boot jars are already built. It does NOT assume the services are running — it starts
 * them itself, because owning the process is the only way to SIGKILL and restart one.
 *
 * <p>{@link #preflight()} exists because every one of these failures produces a
 * baffling downstream symptom if you let it through. A missing Toxiproxy proxy shows up
 * as "the packet-loss scenario passed suspiciously easily". A missing Debezium connector
 * shows up as "every saga is stuck". Fail loudly, at the start, with the fix in the
 * message.
 */
public final class ChaosStack implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ChaosStack.class);

    public static final String JDBC   = "jdbc:postgresql://localhost:5432/finflow";
    public static final String DB_USER = "finflow";
    public static final String DB_PASS = "finflow";

    public static final String CHAOS_API   = "http://localhost:9000";
    public static final String SAGA_API    = "http://localhost:8088";
    public static final String TOXIPROXY   = "http://localhost:8474";
    public static final String CONNECT_API = "http://localhost:8083";

    /** Kafka THROUGH Toxiproxy. Overridable with -Dfinflow.kafka.bootstrap. */
    public static final String KAFKA_BOOTSTRAP =
            System.getProperty("finflow.kafka.bootstrap", "localhost:26092");

    private static final Duration BOOT_TIMEOUT = Duration.ofSeconds(90);

    /**
     * Only the services on the rebalance-saga critical path:
     *   saga-orchestrator -> outbox -> Debezium -> saga.commands.aws
     *     -> aws-adapter-worker -> chaos-api -> outbox -> Debezium -> saga.events
     *       -> saga-orchestrator (loop x5)
     *
     * <p>The ingestors, cost-normalizer, commitment-tracker and query-api are on the
     * INGEST path and play no part in a rebalance. Not starting them keeps the run
     * fast and — more usefully — means cost_ledger is FROZEN for the duration, so any
     * ledger delta at all is a genuine violation rather than background noise.
     *
     * <p>Insertion-ordered on purpose: chaos-api first, because the other two log
     * connection errors on boot if their downstream isn't up yet. Map.of() would NOT
     * preserve this — its iteration order is deliberately randomised.
     */
    private static final Map<String, Integer> SAGA_PATH = new LinkedHashMap<>();
    static {
        SAGA_PATH.put("chaos-api", 9000);
        SAGA_PATH.put("saga-orchestrator", 8088);
        SAGA_PATH.put("aws-adapter-worker", 8089);
    }

    private final Path repoRoot;
    private final Path logDir;
    private final Map<String, ServiceProcess> services = new LinkedHashMap<>();

    public final ChaosApiControl chaos;
    public final ToxiproxyControl toxiproxy;
    public final SagaDriver sagas;
    public final KafkaLag kafkaLag;
    public final SnapshotReader snapshots;

    public ChaosStack() throws SQLException {
        this.repoRoot = Path.of(System.getProperty("finflow.repo.root", "."));
        this.logDir = repoRoot.resolve("build/chaos-logs");
        this.chaos = new ChaosApiControl(CHAOS_API);
        this.toxiproxy = new ToxiproxyControl(TOXIPROXY);
        this.sagas = new SagaDriver(SAGA_API);
        this.kafkaLag = new KafkaLag(KAFKA_BOOTSTRAP);
        this.snapshots = new SnapshotReader(JDBC, DB_USER, DB_PASS, kafkaLag);
    }

    /** Fail fast, loudly, with the fix in the message. */
    public void preflight() {
        require(Http.isUp(TOXIPROXY + "/version"),
                "Toxiproxy admin API is not answering on :8474.\n"
                        + "  -> docker compose -f docker-compose.yml -f docker-compose.chaos.yml up -d");

        require(toxiproxy.proxyExists(),
                "Toxiproxy has no 'kafka' proxy.\n"
                        + "  -> infra/docker/toxiproxy/toxiproxy.json must contain it, then RECREATE the container:\n"
                        + "     docker compose -f docker-compose.yml -f docker-compose.chaos.yml up -d --force-recreate toxiproxy");

        require(Http.isUp(CONNECT_API + "/connectors"),
                "Kafka Connect is not answering on :8083. Debezium must be running or no outbox row ever reaches Kafka.");

        for (var e : SAGA_PATH.entrySet()) {
            Path jar = jarFor(e.getKey());
            require(jar != null && Files.exists(jar),
                    "No boot jar for " + e.getKey() + ".\n  -> ./gradlew bootJar");
        }
        log.info("preflight OK — infra up, toxiproxy proxy present, jars built");
    }

    /** Start every service on the saga path, pointed at Kafka VIA TOXIPROXY. */
    public void startSagaPath() throws IOException {
        for (var e : SAGA_PATH.entrySet()) {
            ServiceProcess p = new ServiceProcess(
                    e.getKey(), jarFor(e.getKey()), e.getValue(), KAFKA_BOOTSTRAP, logDir);
            services.put(e.getKey(), p);
            p.start(BOOT_TIMEOUT);
        }
    }

    public ServiceProcess service(String name) {
        ServiceProcess p = services.get(name);
        if (p == null) throw new IllegalArgumentException("chaos suite does not own service: " + name);
        return p;
    }

    /**
     * services/&lt;name&gt;/build/libs/&lt;name&gt;-0.1.0-SNAPSHOT.jar
     *
     * <p>bootJar ALSO emits a "-plain.jar" (the non-executable library jar). Running
     * that one produces "no main manifest attribute" and a confusing 20 minutes.
     * Glob and exclude it rather than hard-coding a version that will drift.
     */
    private Path jarFor(String service) {
        Path libs = repoRoot.resolve("services").resolve(service).resolve("build/libs");
        if (!Files.isDirectory(libs)) return null;
        try (Stream<Path> s = Files.list(libs)) {
            List<Path> jars = s.filter(p -> p.getFileName().toString().endsWith(".jar"))
                    .filter(p -> !p.getFileName().toString().endsWith("-plain.jar"))
                    .toList();
            return jars.isEmpty() ? null : jars.get(0);
        } catch (IOException e) {
            return null;
        }
    }

    private static void require(boolean ok, String message) {
        if (!ok) throw new IllegalStateException("PREFLIGHT FAILED\n" + message);
    }

    @Override
    public void close() {
        try { chaos.reset(); }        catch (RuntimeException ignored) { }
        try { toxiproxy.healAll(); }  catch (RuntimeException ignored) { }
        services.values().forEach(ServiceProcess::stopQuietly);
        try { snapshots.close(); }    catch (SQLException ignored) { }
        kafkaLag.close();
    }
}
