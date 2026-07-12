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
    // outbox.pending gauge). Declared as compileOnly + optional so the library
    // still works in a hypothetical consumer without Micrometer on the
    // classpath — the metrics auto-config is guarded by @ConditionalOnClass.
    // Every FinFlow service DOES have micrometer (via actuator + the prometheus
    // registry added on Day 22), so in practice the metrics are always active.
    compileOnly("io.micrometer:micrometer-core")

    // --- test: validate against a real Postgres ---
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("io.micrometer:micrometer-core")
    testRuntimeOnly("org.flywaydb:flyway-database-postgresql")
    testRuntimeOnly("org.postgresql:postgresql")
}
