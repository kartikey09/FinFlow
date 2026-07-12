plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management") //Spring Dependency Management plugin to pull the versions from BOM
}

dependencies{
    //the dependencies don't have a version number at the end as BOM in the root project is managing the versions globally.
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    // Day 22: Prometheus registry — exposes /actuator/prometheus for scraping.
    implementation("io.micrometer:micrometer-registry-prometheus")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}