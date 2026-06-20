package io.finflow.ingestion.aws.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * What this module does:
 * This configuration class tells Spring Boot exactly where to look to find
 * database tables (@Entity) and database access objects (Repositories) across
 * multiple distinct Gradle modules.
 *
 * Why we built it (The "Shared Library" Trap):
 * By default, when Spring Boot starts up, it only looks for components in the
 * current folder and downwards. The `aws-ingestor` lives in `io.finflow.ingestion.aws`.
 * Our outbox library lives entirely outside that tree in `io.finflow.outbox`.
 * If we don't explicitly tell Spring to look in the outbox folder, it will
 * never build the outbox tables for this microservice.
 *
 * The Senior Engineer Flex (Why the library doesn't do this itself):
 * You might ask: "Why didn't we just put @EntityScan inside the
 * OutboxAutoConfiguration?"
 * Because in Spring, if a shared library declares `@EntityScan`, it can
 * accidentally OVERRIDE the host application's default scanning. If the library
 * did that, the `aws-ingestor` would successfully load the outbox tables, but
 * it would suddenly forget how to load its own billing tables!
 * The safest architectural pattern is to force the "adopter" (the ingestor)
 * to explicitly declare all the package roots it needs.
 */

@Configuration
// Instructs Hibernate/JPA: "Look in these two exact folders for any class
// annotated with @Entity, and map them to PostgreSQL tables."
// Folder 1: Finds `PollCursor` and `CostLineItemRaw` (the ingestor's own data).
// Folder 2: Finds `OutboxEvent` (the shared library's data).
@EntityScan(basePackages = {"io.finflow.ingestion.aws","io.finflow.outbox"})
// Instructs Spring Data: "Look in these two exact folders for any interface
// extending JpaRepository, and auto-generate the SQL implementation for it."
// Without "io.finflow.outbox" here, the `OutboxEventRepository` bean is never
// created. If that bean is missing, our `OutboxRetentionAutoConfiguration`
// gracefully backs off and our outbox breaks quietly.
@EnableJpaRepositories(basePackages = {"io.finflow.ingestion.aws","io.finflow.outbox"})
public class JpaConfig {
}

// the class is empty with no methds or var. Its just there for to hold these 2 annotations