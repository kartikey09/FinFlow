plugins {
    java
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencies {
    implementation(project(":shared:common"))
    // NOTE: no outbox-starter — query-api is a pure sink. It writes NO events
    // downstream (only reads its own projection tables). This is the first
    // service NOT to be a producer, which is exactly the CQRS shape.

    implementation("org.springframework.boot:spring-boot-starter-web")
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
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.kafka:spring-kafka")
    implementation("jakarta.persistence:jakarta.persistence-api")

    runtimeOnly("org.postgresql:postgresql")
    implementation("org.flywaydb:flyway-core")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")

    // --- tests ---
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.kafka:spring-kafka-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testRuntimeOnly("com.h2database:h2")
}
