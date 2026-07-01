plugins {
    java
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    // versions come from the root subprojects block (Spring dependency-management + BOM)
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.kafka:spring-kafka")
    implementation(project(":shared:common"))
    implementation(project(":shared:outbox-starter"))   // Day 13: cost-normalizer is now a producer too
    implementation("jakarta.persistence:jakarta.persistence-api")

    // Day 13 — L1 dedup cache (last 100k event ids in front of the DB ledger).
    implementation("com.github.ben-manes.caffeine:caffeine:3.1.8")

    runtimeOnly("org.postgresql:postgresql")
    implementation("org.flywaydb:flyway-core")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")

    // --- tests ---
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.kafka:spring-kafka-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")

    testImplementation("org.testcontainers:kafka")
    testImplementation("io.debezium:debezium-testing-testcontainers:2.7.0.Final")

    testRuntimeOnly("com.h2database:h2")
}
