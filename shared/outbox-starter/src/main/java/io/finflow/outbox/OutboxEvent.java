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
 * One row of the transactional outbox. Maps to the outbox_event table created
 * by V1__create_outbox_event.sql.
 *
 * The payload is a String annotated with @JdbcTypeCode(SqlTypes.JSON) so
 * Hibernate binds it to the Postgres jsonb column directly — we keep it as a
 * String (already-serialized JSON) rather than a typed object so the outbox
 * stays event-shape-agnostic: any service can write any event without this
 * library knowing the event's class.
 */


@Entity
@Table(name = "outbox_event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // Generates the protected constructor for JPA
@AllArgsConstructor // Generates the public constructor for your code
public class OutboxEvent {
    @Id
    private UUID id;

    @Column(name = "aggregate_type", nullable = false)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private String aggregateId;

    @Column(nullable = false)
    private String type;

    @JdbcTypeCode(SqlTypes.JSON)
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