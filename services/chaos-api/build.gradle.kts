plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management") //Spring Dependency Management plugin to pull the versions from BOM
}

dependencies{
    //the dependencies don't have a version number at the end as BOM in the root project is managing the versions globally.
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    //the root build.gradle.kts has the test dependencies already - JUnit5, MockMvc, awaitility,etc
    //not adding here
}