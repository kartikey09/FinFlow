package io.finflow.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * The Java blueprint for the Transactional Outbox database table.
 *
 * What this does:
 * This class maps directly to the `outbox_event` table in PostgreSQL. Every time
 * your application creates one of these objects and saves it, Hibernate translates
 * it into a new database row.
 * wrote the SQL to create the outbox_event table inside your PostgreSQL database.
 * However, your Java application doesn't natively speak SQL, it needs "Translator" or "Bridge"
 * so that when your Java code wants to save an event, it knows exactly which columns exist and
 * what data types they require.It uses the Java Persistence API (JPA) and Hibernate. It mirrors the SQL table we made.
 *
 * The clever design choice:
 * The `payload` (the actual event data) is stored as a simple text String instead
 * of a specific Java object (like a BillingEvent). This keeps this outbox library
 * completely generic. Any microservice can convert its unique data into a JSON
 * string and save it here, and this library never needs to know or care what the
 * data actually looks like.
 */


@Entity
@Table(name = "outbox_event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // Generates the protected constructor for JPA
@AllArgsConstructor // Generates the public constructor for your code
public class OutboxEvent {
    @Id
    private UUID id;

    // These three fields map directly to the text columns in your SQL table. The @Column(name = "...") annotation
    // is only strictly necessary if the Java variable name is different from the database column name
    // (e.g., aggregateType vs aggregate_type), but adding nullable = false ensures Java will throw an error
    // if you try to save a blank value, acting as a great safety net.
    @Column(name = "aggregate_type", nullable = false)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private String aggregateId;

    @Column(nullable = false)
    private String type;

    @JdbcTypeCode(SqlTypes.JSON)//Hibernate 6 feature.
    // It automatically translates the raw Java text string into binary JSON when saving to the database,
    // and translates it back to text when reading.
    @Column(nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

}

/*
    How Hibernate Uses Reflection on OutboxEvent
    When Hibernate reads a row from your outbox_event Postgres table, it needs to turn that SQL data into your Java
    OutboxEvent object.
    The problem? Hibernate's core code was written years ago. The creators of Hibernate have no idea what an OutboxEvent is,
    what its fields are named, or what arguments your custom constructor requires.

    So, Hibernate uses reflection to build your object dynamically. Here is the step-by-step process happening under
    the hood:

    1 .Finding the Class: Hibernate looks at your mapping and uses reflection to load your class into memory.

    2. The No-Arg Constructor Requirement: Hibernate needs to create a blank "shell" of your object before it
        populates the data. Because it doesn't know what arguments your custom constructor takes, it uses reflection to
        search specifically for a constructor with zero arguments (OutboxEvent()). It then forcefully executes it to
        instantiate the object. This is exactly why your class requires that protected no-arg constructor.

    3. Bypassing Encapsulation: Notice that your OutboxEvent only exposes getters, making it "read-only" from the
        outside. How does Hibernate set the data if there are no setters? Reflection allows Hibernate to ignore Java's
        private or protected keywords. It grabs the blank object, forcefully opens up your private fields (like payload),
        and injects the database values directly into them.
 */