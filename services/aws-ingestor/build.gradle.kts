plugins {
    java
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    // io.spring.dependency-management + the Spring BOM + the universal test
    // stack are applied by the root `subprojects` block, so no versions here.
}

dependencies {
    implementation(project(":shared:common"))
    implementation(project(":shared:outbox-starter"))

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
    // aop - AspectJ - required to process Resilience4j annotations
    implementation("org.springframework.boot:spring-boot-starter-aop")
    implementation("org.springframework.kafka:spring-kafka")

    // adds fault tolerance capabilities to spring.
    // enables core patterns like retires, rate limiters, circuit breakers
    implementation("io.github.resilience4j:resilience4j-spring-boot3:2.2.0")

    runtimeOnly("org.postgresql:postgresql")
    implementation("org.flywaydb:flyway-core")
    // Flyway 10+ splits DB support into modules; Postgres needs this explicitly.
    runtimeOnly("org.flywaydb:flyway-database-postgresql")

    // --- test ---
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.springframework.kafka:spring-kafka-test")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:kafka")
    // spring-boot-starter-test is supplied by the root subprojects test stack.
}
