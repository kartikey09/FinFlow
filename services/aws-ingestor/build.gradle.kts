plugins {
    java
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    // io.spring.dependency-management + the Spring BOM + the universal test
    // stack are applied by the root `subprojects` block, so no versions here.
}

dependencies {
    implementation(project(":shared:common"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.kafka:spring-kafka")

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
