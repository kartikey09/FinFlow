/*
 * settings.gradle.kts — Top-level Gradle settings for the FinFlow monorepo.
 *
 * Module strategy: we list directories for every planned service so the
 * structure is visible from day 1, but only `include()` modules that have
 * actual build files. Modules are uncommented as they get built across
 * the 6 weeks. On day 1, nothing is included yet — we're just standing
 * up the build system itself.
 */

rootProject.name = "finflow"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

// Modules will be uncommented as they're built:
//
// Day 2-4 (Chaos API):
include("services:chaos-api")
//
// Day 5 (first service shell):
include("shared:common")
 include("services:aws-ingestor")
//
// Week 2 (outbox library + Debezium):
 include("shared:outbox-starter")
//
// Week 3 (ingestion + read side):
// include("services:gcp-ingestor")
// include("services:cost-normalizer")
// include("services:commitment-tracker")
// include("services:query-api")
//
// Week 4 (saga):
// include("services:saga-orchestrator")
// include("services:aws-adapter-worker")
// include("services:gcp-adapter-worker")
//
// Week 6 (recommendations):
// include("services:recommendation-engine")
