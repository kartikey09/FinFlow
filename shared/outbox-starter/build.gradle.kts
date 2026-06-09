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
    // Exposed to consumers: they compile against OutboxEvent/Repository/Appender,
    // which need JPA + transactions; and the appender serializes with Jackson.
    api("org.springframework.boot:spring-boot-starter-data-jpa")
    api("com.fasterxml.jackson.core:jackson-databind")
    // For @AutoConfiguration / @ConditionalOn* (also transitive via data-jpa).
    implementation("org.springframework.boot:spring-boot-autoconfigure")

    // --- test: validate against a real Postgres ---
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.flywaydb:flyway-core")
    testRuntimeOnly("org.flywaydb:flyway-database-postgresql")
    testRuntimeOnly("org.postgresql:postgresql")
}
