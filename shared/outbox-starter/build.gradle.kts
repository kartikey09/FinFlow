plugins {
    `java-library`
    id("io.spring.dependency-management")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:3.3.2")
    }
}

dependencies {
    // Exposed to consumers.
    api("org.springframework.boot:spring-boot-starter-data-jpa")
    api("com.fasterxml.jackson.core:jackson-databind")
    // Day 20: Flyway is now part of the library's contract because
    // OutboxSchemaAutoConfiguration runs the migration for consumers.
    api("org.flywaydb:flyway-core")

    implementation("org.springframework.boot:spring-boot-autoconfigure")

    // Day 22: Micrometer for outbox metrics (events.published counter,
    // outbox.pending gauge). compileOnly so the library still works in a
    // consumer without Micrometer — the metrics auto-config is guarded by
    // @ConditionalOnClass.
    compileOnly("io.micrometer:micrometer-core")

    // Day 24: Micrometer Tracing, for capturing the W3C traceparent onto each
    // outbox row (see OutboxAppender.currentTraceParent). Same compileOnly
    // pattern: the Tracer/Propagator are injected via ObjectProvider and are
    // null when absent, so a consumer without tracing appends events exactly as
    // before with a null trace_parent column.
    //
    // NOTE: micrometer-tracing (the API) — NOT micrometer-tracing-bridge-otel.
    // The library only needs the abstraction; each SERVICE picks the bridge.
    compileOnly("io.micrometer:micrometer-tracing")

    // --- test: validate against a real Postgres ---
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("io.micrometer:micrometer-core")
    testImplementation("io.micrometer:micrometer-tracing")
    testRuntimeOnly("org.flywaydb:flyway-database-postgresql")
    testRuntimeOnly("org.postgresql:postgresql")
}
