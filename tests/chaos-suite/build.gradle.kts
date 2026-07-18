/*
 * Day 25 — the chaos test suite.
 *
 * NOT a Spring Boot service, and NOT a Testcontainers module. It is an external
 * DRIVER: it spawns the real service jars, drives the real Kafka / Postgres /
 * Debezium stack already running in docker-compose, breaks things on purpose, and
 * then asserts invariants by reading Postgres directly.
 *
 * WHY NOT TESTCONTAINERS — two reasons, and the second is the real one:
 *   1. Testcontainers ITs don't run on this machine (the known Docker runtime issue).
 *   2. More fundamentally: FinFlow's services do NOT run in containers. They run on
 *      the HOST via `./gradlew bootRun`. There is no `saga-orchestrator` container to
 *      `docker kill`. Testcontainers could not express this test even if it worked.
 *      Owning the service as a java.lang.Process and calling destroyForcibly() both
 *      works AND is a purer SIGKILL.
 *
 * NOTE ON DEPENDENCIES: the ROOT subprojects{} block already gives every module
 * junit-jupiter, assertj-core, junit-platform-launcher AND the Spring Boot BOM on
 * test scope. Re-declaring a junit-bom here would fight it. So test-scope deps are
 * declared WITHOUT versions (the Spring BOM manages them); only the compile-scope
 * deps, which the BOM does not cover, are pinned.
 *
 * TWO TIERS:
 *   ./gradlew :tests:chaos-suite:test       -> InvariantsTest only. Pure logic, no
 *                                              Docker, no network. Always green, so
 *                                              `./gradlew build` never needs a stack.
 *   ./gradlew :tests:chaos-suite:chaosTest  -> the 4 live scenarios.
 */

import java.time.Duration

plugins {
    java
}

dependencies {
    // Postgres — invariants are asserted by reading the DB, because that is the only
    // source of truth that survives a service being SIGKILLed.
    implementation("org.postgresql:postgresql:42.7.3")

    // Kafka AdminClient — authoritative consumer-group lag. Deliberately NOT scraping
    // kafka-exporter's /metrics (Day 23): a chaos test that fails because Grafana is
    // down is a test nobody trusts.
    implementation("org.apache.kafka:kafka-clients:3.7.1")

    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")
    implementation("org.slf4j:slf4j-api:2.0.13")
    runtimeOnly("ch.qos.logback:logback-classic:1.5.6")

    // HTTP: the JDK's java.net.http.HttpClient. No dependency needed.

    // junit-jupiter / assertj / junit-platform-launcher come from the ROOT build.
    // Version managed by the Spring BOM the root puts on test scope.
    testImplementation("org.awaitility:awaitility")
}

// Tier 1 — the default `test` task runs ONLY the pure unit tests.
// `./gradlew build` must stay green with nothing running.
tasks.test {
    include("**/InvariantsTest.class")
}

// Tier 2 — the live chaos scenarios. Opt-in; never part of `build`.
val chaosTest by tasks.registering(Test::class) {
    group = "verification"
    description = "Live chaos scenarios against a RUNNING stack. Needs: docker compose up + ./gradlew bootJar."

    include("**/ChaosSuiteIT.class")
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath

    // Long by design: 4 scenarios x 20 sagas, plus kill/restart cycles.
    timeout.set(Duration.ofMinutes(25))
    testLogging {
        events("passed", "failed", "skipped")
        showStandardStreams = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }

    // So the harness can find services/<name>/build/libs/*.jar
    systemProperty("finflow.repo.root", rootProject.projectDir.absolutePath)
    // Chaos runs go through Toxiproxy, NOT Kafka's direct external listener (29092).
    systemProperty("finflow.kafka.bootstrap",
        System.getProperty("finflow.kafka.bootstrap", "localhost:26092"))

    // Never use a stale result — chaos is nondeterministic by construction.
    outputs.upToDateWhen { false }
}
