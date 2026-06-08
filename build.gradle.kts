/*
 * Root build.gradle.kts — shared config inherited by every subproject.
 *
 * Plugins declared with `apply false` are available to subprojects but
 * not applied here at the root (the root produces no jar of its own).
 */

plugins {
    id("org.springframework.boot") version "3.3.2" apply false
    id("io.spring.dependency-management") version "1.1.6" apply false
    java
}

allprojects {
    group = "io.finflow"
    version = "0.1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java")

    java {
        // Pin Java 21 across every module. Gradle auto-downloads a matching
        // JDK if the local machine doesn't have one, so anyone cloning the
        // repo can build without installing Java separately.
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
            showStandardStreams = false
        }
    }

    // Common compile options
    tasks.withType<JavaCompile> {
        options.compilerArgs.addAll(
            listOf(
                "-parameters",       // preserve parameter names for Spring
                "-Xlint:unchecked",
                "-Xlint:deprecation"
            )
        )
        options.encoding = "UTF-8"
    }
//
    val lombokVersion = "1.18.36"

    dependencies {
        // Pull in the Spring Boot BOM for test scope using Gradle's native platform()
        // mechanism. This version-manages JUnit, AssertJ, Mockito, etc. for every
        // module without needing the io.spring.dependency-management plugin at root.
        // Service modules still apply that plugin themselves for their compile scope.
        "testImplementation"(platform("org.springframework.boot:spring-boot-dependencies:3.3.2"))

        // Lombok — compile-time only; available in every module
        "compileOnly"("org.projectlombok:lombok:$lombokVersion")
        "annotationProcessor"("org.projectlombok:lombok:$lombokVersion")
        "testCompileOnly"("org.projectlombok:lombok:$lombokVersion")
        "testAnnotationProcessor"("org.projectlombok:lombok:$lombokVersion")

        // JUnit 5 — version managed by Spring BOM above
        // junit-platform-launcher must be explicit for Gradle 8+
        "testImplementation"("org.junit.jupiter:junit-jupiter")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")

        // AssertJ — fluent assertions used alongside JUnit 5 in every module
        "testImplementation"("org.assertj:assertj-core")
    }
}
