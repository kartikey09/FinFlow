plugins {
    `java-library`
    // No Spring Boot plugin here: this is a plain library, not a runnable app.
    // Dependency versions are managed by the root subprojects block (Spring BOM).
}

dependencies {
    // Deliberately dependency-free for now — just shared domain constants.
    // The canonical cost model and shared Kafka serde config will land here in
    // Weeks 2–3 (extracted from the first service that needs to share them).
}
