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
        options.compilerArgs.addAll(listOf(
            "-parameters",       // preserve parameter names for Spring
            "-Xlint:unchecked",
            "-Xlint:deprecation"
        ))
        options.encoding = "UTF-8"
    }
}
