# FinFlow Debezium Outbox Connector

This configuration file (`connector.json`) is the bridge between the FinFlow PostgreSQL database and the Kafka cluster. It configures **Debezium**, an open-source Change Data Capture (CDC) platform, to monitor the Postgres Write-Ahead Log (WAL) and stream outbox events into Kafka topics in real-time.

## 1. Core Connector Settings
These fields tell the Kafka Connect framework what type of worker to spin up.

* **`name`** (`finflow-outbox-connector`): The unique ID for this connector instance within the Kafka Connect cluster.
* **`config.connector.class`** (`...PostgresConnector`): Instructs Kafka Connect to use the specific Debezium PostgreSQL driver.
* **`config.tasks.max`** (`1`): **Crucial.** This restricts the connector to a single thread. In CDC, processing the database log must happen sequentially to guarantee strict event ordering. Using more than 1 task would scramble the order of your events.

## 2. Database Connection & Identification
Standard credentials pointing Debezium to your PostgreSQL instance.

* **`database.hostname`**, **`database.port`**, **`database.user`**, **`database.password`**, **`database.dbname`**: The physical connection details to the `finflow` database.
* **`topic.prefix`** (`finflow`): A logical namespace identifier for this specific database server. Debezium uses this as a prefix for internal tracking topics.

## 3. PostgreSQL Logical Replication
This section configures *how* Debezium physically reads the database. It utilizes native PostgreSQL features rather than polling the database with `SELECT` queries.

* **`plugin.name`** (`pgoutput`): Tells Debezium to use the standard, native logical replication decoder built into PostgreSQL (versions 10+). 
* **`slot.name`** (`finflow_outbox_slot`): The name of the replication slot Postgres will create to track Debezium's progress. If Debezium goes offline, Postgres keeps the WAL safe in this slot until Debezium reconnects.
* **`publication.name`** (`finflow_outbox_pub`): A Postgres "Publication" acts as a filter, dictating which table changes are sent to the replication slot.
* **`publication.autocreate.mode`** (`all_tables`): Automatically creates the publication in Postgres if it doesn't already exist.

## 4. Target Scoping & Lifecycle
Defines exactly what data Debezium should care about and how it should handle historical data.

* **`table.include.list`** (`ingestion.outbox_event`): **Crucial.** Tells Debezium to ignore all other tables (like your cursor or business tables) and ONLY stream changes that happen in the `outbox_event` table.
* **`snapshot.mode`** (`initial`): When Debezium boots up for the very first time, it will run a standard `SELECT *` to capture any rows currently sitting in the table before switching over to tailing the WAL.
* **`tombstones.on.delete`** (`false`): Eventually, you will run a cleanup job to delete old, processed events from the `outbox_event` table. Setting this to false prevents Debezium from sending empty "delete" messages to Kafka when that cleanup happens.
* **`heartbeat.interval.ms`** (`10000`): Sends a tiny ping every 10 seconds to keep the Postgres replication slot active, even if no outbox events are being generated.

## 5. Kafka Converters
Dictates how the extracted data is serialized into byte arrays before being pushed into Kafka.

* **`key.converter`** (`StringConverter`): We want the Kafka message key (the `aggregate_id`) to be sent as a plain text string so downstream consumers can easily read and partition by it.
* **`value.converter`** (`JsonConverter`): We want the message payload sent as JSON.
* **`value.converter.schemas.enable`** (`false`): By default, Debezium wraps every message in a massive, heavy JSON schema defining the table structure. Setting this to false strips all that away, sending only the clean, raw data.

## 6. Dead Letter Queue (DLQ) & Error Handling
Dictates how Kafka Connect behaves when it encounters a malformed or "poison" record it cannot process, ensuring a single bad database row does not crash the entire data pipeline.

* **`errors.tolerance`** (`all`): Changes the default failure policy from crashing the entire connector to quarantining the bad record. It allows the connector to drop the failed row from the main pipeline and continue processing the next healthy row.
* **`errors.deadletterqueue.topic.name`** (`finflow.dlq.outbox-connector`): Defines the specific Kafka topic (the DLQ) where failed records are safely routed instead of being permanently lost.
* **`errors.deadletterqueue.topic.replication.factor`** (`1`): Forces the auto-creation of the DLQ topic to use a replication factor of 1. This is strictly required for local development since our Docker Compose stack only runs a single Kafka broker.
* **`errors.deadletterqueue.context.headers.enable`** (`true`): Appends diagnostic metadata headers to the failed message inside the DLQ. This includes the exact Java exception and stack trace, providing a built-in autopsy report for the failure.
* **`errors.log.enable`** (`true`): Instructs Kafka Connect to print explicit `ERROR` warnings directly to the `finflow-connect` Docker container logs whenever a record is routed to the DLQ.
* **`errors.log.include.messages`** (`true`): Injects the actual physical JSON payload of the corrupted record directly into the terminal logs alongside the error. This allows you to instantly spot the bad data without having to manually open Kafka UI and hunt through the DLQ topic.

## 7. The Magic: Single Message Transform (SMT)
By default, Debezium sends a massive payload containing the "before" and "after" state of the entire database row. We don't want that. We want a clean domain event. The **Outbox Event Router SMT** acts as an interceptor, reshaping the raw database row into a perfect Kafka message.

* **`transforms`** (`outbox`): Activates the outbox transform plugin.
* **`transforms.outbox.type`** (`...EventRouter`): Uses Debezium's official outbox pattern router.

**Field Mappings:**
These tell the SMT which columns in your `outbox_event` table correspond to the standard Outbox pattern fields:
* **`...event.id`** (`id`): The UUID, used for message deduplication.
* **`...event.key`** (`aggregate_id`): Becomes the **Kafka Key**. This guarantees that all events for a specific AWS account or billing report go to the exact same Kafka partition in the exact order they occurred.
* **`...event.type`** (`type`): Sent as a Kafka header so consumers know what kind of event this is without having to parse the JSON.
* **`...event.timestamp`** (`created_at`): Sets the official Kafka message timestamp.
* **`...event.payload`** (`payload`): The column containing your actual serialized business data.

**Payload & Routing Execution:**
* **`...expand.json.payload`** (`true`): **Crucial.** Because your `payload` column is already a JSON string, Debezium would normally escape it (e.g., `"{\"item\": 1}"`). Setting this to true un-escapes it so Kafka receives a native, nested JSON object.
* **`...route.by.field`** (`aggregate_type`): Tells the router to look at the `aggregate_type` column (e.g., "billing" or "user") to decide where this message goes.
* **`...route.topic.replacement`** (`finflow.events.${routedByValue}`): The actual routing logic. If `aggregate_type` is "billing", this message is automatically pushed to the Kafka topic named `finflow.events.billing`.