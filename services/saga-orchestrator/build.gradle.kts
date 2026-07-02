plugins {
    java
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencies {
    implementation(project(":shared:common"))

    // The plan is emphatic: Day 16 uses JPA + Jackson and nothing else.
    // Spring Kafka + outbox-starter come in on Day 17 when we wire this to the
    // live system. Keeping Day 16 minimal is the whole point — a state machine
    // that's provably correct in a pure JUnit test before any infrastructure.
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("com.fasterxml.jackson.core:jackson-databind")

    runtimeOnly("org.postgresql:postgresql")
    implementation("org.flywaydb:flyway-core")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")

    // --- tests: pure JUnit + Mockito. No @SpringBootTest yet — the transition
    //     service is a pure function, tested without booting anything.
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
