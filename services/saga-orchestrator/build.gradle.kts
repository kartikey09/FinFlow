plugins {
    java
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencies {
    implementation(project(":shared:common"))
    implementation(project(":shared:outbox-starter"))     // Day 17: saga becomes the 5th outbox producer

    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-web")       // Day 17: REST endpoint
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    // Day 22: Prometheus registry — exposes /actuator/prometheus for scraping.
    implementation("io.micrometer:micrometer-registry-prometheus")
    // Day 24: distributed tracing.
    //   micrometer-tracing-bridge-otel -> Micrometer's Tracer/Propagator API,
    //     backed by the OpenTelemetry SDK. This is what makes Spring's HTTP +
    //     Kafka observations actually produce spans and inject/extract W3C headers.
    //   opentelemetry-exporter-otlp    -> ships those spans to the OTel Collector.
    // Versions come from the Spring BOM (root build.gradle.kts) — do not pin.
    implementation("io.micrometer:micrometer-tracing-bridge-otel")
    implementation("io.opentelemetry:opentelemetry-exporter-otlp")
    implementation("org.springframework.kafka:spring-kafka")                 // Day 17: consumer for saga.events
    implementation("com.fasterxml.jackson.core:jackson-databind")

    runtimeOnly("org.postgresql:postgresql")
    implementation("org.flywaydb:flyway-core")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")

    // --- tests ---
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.kafka:spring-kafka-test")

    // Day 20: Testcontainers-backed integration tests (src/test/java/io/finflow/saga/it).
    // Real Postgres + Kafka. Run via the dedicated `integrationTest` task (needs Docker),
    // NOT part of the default `test` run — see the task config below.
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:kafka")
    testImplementation("org.awaitility:awaitility")
}

// Day 20: keep the fast, hermetic suite (unit + no-Docker *Test) as the default
// `test` run — green without any external infrastructure. The Testcontainers ITs
// in the `io.finflow.saga.it` package are compiled with the rest of the test
// sources but only executed by the opt-in `integrationTest` task, which requires
// a running Docker daemon.
tasks.named<Test>("test") {
    exclude("io/finflow/saga/it/**")
}

val integrationTest by tasks.registering(Test::class) {
    description = "Runs the Testcontainers-backed saga integration tests (requires Docker)."
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    include("io/finflow/saga/it/**")
    shouldRunAfter(tasks.named("test"))
}
