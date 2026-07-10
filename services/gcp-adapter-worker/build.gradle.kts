plugins {
    java
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencies {
    implementation(project(":shared:common"))
    implementation(project(":shared:outbox-starter"))          // 7th outbox producer

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    // Day 22: Prometheus registry — exposes /actuator/prometheus for scraping.
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-aop")   // resilience4j aspects need AOP
    implementation("org.springframework.kafka:spring-kafka")
    implementation("com.fasterxml.jackson.core:jackson-databind")

    // Same Resilience4j stack as aws-adapter-worker — retry + circuit breaker + bulkhead.
    // "TimeLimiter" is realized as HTTP read-timeout in GcpChaosClientConfig (same
    // rationale documented on Day 18 — avoid the CompletableFuture cascade).
    implementation("io.github.resilience4j:resilience4j-spring-boot3:2.2.0")

    runtimeOnly("org.postgresql:postgresql")
    implementation("org.flywaydb:flyway-core")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.kafka:spring-kafka-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testRuntimeOnly("com.h2database:h2")
}
