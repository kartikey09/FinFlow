package io.finflow.chaos.harness;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * One FinFlow service, owned as a real OS process so we can murder it.
 *
 * <h2>Why not `docker kill`, which is what the plan says?</h2>
 *
 * <p>Because THERE IS NO CONTAINER. FinFlow's services run on the HOST via
 * {@code ./gradlew bootRun}; only the infrastructure (Postgres, Kafka, Connect,
 * Toxiproxy) lives in docker-compose. {@code docker kill saga-orchestrator} would
 * return "No such container". The plan's scenario is not expressible against this
 * architecture as written.
 *
 * <p>So the harness LAUNCHES the services itself, from their boot jars, and holds the
 * {@link Process} handle. {@code destroyForcibly()} sends SIGKILL — the same signal
 * {@code docker kill} sends by default. Same fault, and arguably a purer one: no
 * container runtime in between, no graceful stop, no shutdown hook, no offset commit.
 * The JVM simply ceases to exist mid-transaction.
 *
 * <p>Owning the process is also the only way to RESTART it, and the restart is the
 * actual test: Day 17 built resume-in-flight logic (SagaInstanceRepository.findAllInFlight())
 * that is supposed to pick up orphaned sagas on boot. This is the first time anything
 * has ever proven it works.
 */
public final class ServiceProcess {

    private static final Logger log = LoggerFactory.getLogger(ServiceProcess.class);

    private final String name;
    private final Path jar;
    private final int port;
    private final String kafkaBootstrap;
    private final Path logDir;

    private Process process;

    public ServiceProcess(String name, Path jar, int port, String kafkaBootstrap, Path logDir) {
        this.name = name;
        this.jar = jar;
        this.port = port;
        this.kafkaBootstrap = kafkaBootstrap;
        this.logDir = logDir;
    }

    public String name() { return name; }
    public int port()    { return port; }

    public boolean isAlive() {
        return process != null && process.isAlive();
    }

    /**
     * Launch and block until /actuator/health reports UP.
     *
     * <p>SPRING_KAFKA_BOOTSTRAP_SERVERS is injected as an ENVIRONMENT VARIABLE rather
     * than by editing application.yml. That matters: it means the chaos suite points
     * every service at Toxiproxy (localhost:26092) instead of Kafka's direct listener
     * WITHOUT modifying a single committed config file. Nothing about the normal
     * `./gradlew bootRun` workflow changes, and there is no yml diff to accidentally
     * merge into main.
     */
    public void start(Duration timeout) throws IOException {
        if (isAlive()) return;

        List<String> cmd = new ArrayList<>(List.of(
                "java",
                "-XX:+ExitOnOutOfMemoryError",
                "-jar", jar.toAbsolutePath().toString()));

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.environment().put("SPRING_KAFKA_BOOTSTRAP_SERVERS", kafkaBootstrap);
        pb.redirectErrorStream(true);

        Files.createDirectories(logDir);
        File logFile = logDir.resolve(name + ".log").toFile();
        pb.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile));

        this.process = pb.start();
        log.info("started {} (pid={}) -> kafka={} log={}",
                name, process.pid(), kafkaBootstrap, logFile);

        awaitHealthy(timeout);
    }

    /** SIGKILL. Not a graceful shutdown — that would be a different (and much easier) test. */
    public void killHard() {
        if (!isAlive()) {
            log.warn("kill requested for {} but it is already dead", name);
            return;
        }
        long pid = process.pid();
        process.destroyForcibly();
        try {
            process.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        log.info("SIGKILLed {} (pid={}) — no shutdown hook, no offset commit, mid-transaction", name, pid);
    }

    public void restart(Duration timeout) throws IOException {
        log.info("restarting {} — this is where resume-in-flight gets tested", name);
        start(timeout);
    }

    public void awaitHealthy(Duration timeout) {
        String url = "http://localhost:" + port + "/actuator/health";
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            if (Http.isUp(url)) {
                log.info("{} is UP on :{}", name, port);
                return;
            }
            if (process != null && !process.isAlive()) {
                throw new IllegalStateException(name + " died during startup — see "
                        + logDir.resolve(name + ".log"));
            }
            sleep(500);
        }
        throw new IllegalStateException(name + " did not become healthy on :" + port
                + " within " + timeout + " — see " + logDir.resolve(name + ".log"));
    }

    public void stopQuietly() {
        if (process == null) return;
        process.destroy();                       // SIGTERM — teardown, not a test
        try {
            if (!process.waitFor(15, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
    }

    static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
