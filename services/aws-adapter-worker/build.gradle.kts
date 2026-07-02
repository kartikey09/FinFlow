plugins {
    java
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencies {
    implementation(project(":shared:common"))
    implementation(project(":shared:outbox-starter"))          // this service is the 6th outbox producer

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-aop")   // resilience4j aspects need AOP
    implementation("org.springframework.kafka:spring-kafka")
    implementation("com.fasterxml.jackson.core:jackson-databind")

    // Resilience4j — same pattern as aws-ingestor's poll loop, plus Circuit Breaker
    // and Bulkhead which the ingestor didn't need. TimeLimiter is implemented as
    // an HTTP-client-level read timeout (see AwsChaosClientConfig) rather than a
    // Resilience4j @TimeLimiter, because that annotation requires CompletableFuture
    // returns and would cascade Reactor into an otherwise-blocking service.
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
