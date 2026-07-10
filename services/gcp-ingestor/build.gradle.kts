plugins {
    java
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    // versions come from the root subprojects block (Spring BOM + test stack)
}

dependencies {
    implementation(project(":shared:common"))
    implementation(project(":shared:outbox-starter"))   // same outbox library as aws-ingestor

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    // Day 22: Prometheus registry — exposes /actuator/prometheus for scraping.
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-aop")   // resilience4j aspects

    // Bounded retry around the Chaos API poll (same policy as aws-ingestor).
    implementation("io.github.resilience4j:resilience4j-spring-boot3:2.2.0")

    runtimeOnly("org.postgresql:postgresql")
    implementation("org.flywaydb:flyway-core")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")

    // NOTE: no spring-kafka here. Unlike aws-ingestor (which carried it over from
    // its Day 5 shell), this service is built clean — it emits through the OUTBOX,
    // so it never talks to Kafka directly.

    // --- test ---
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
}
