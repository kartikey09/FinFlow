# Debezium connectors

Two connectors watch the same `public.outbox_event` table.

- **finflow-outbox-connector** — the original. Routes rows to
  `finflow.events.${aggregate_type}`. Used by the ingest + normalize + tracker
  + query-api families.
- **finflow-saga-outbox-connector** — new on Day 17. Routes rows to
  `${aggregate_type}` (identity). Used by the saga family: `saga.commands.aws`,
  `saga.commands.gcp`, `saga.events`.

## The overlap, and why it's acceptable

Both connectors see EVERY row. That means:

- An ingest event (`aggregate_type = billing`) is emitted on both
  `finflow.events.billing` (consumed) and `billing` (nobody consumes).
- A saga command (`aggregate_type = saga.commands.aws`) is emitted on both
  `finflow.events.saga.commands.aws` (nobody consumes) and `saga.commands.aws`
  (consumed).

This is a small amount of extra Kafka traffic in exchange for a totally
Groovy-free operational setup. The alternative — a per-row filter via
`io.debezium.transforms.Filter` — needs the `debezium-scripting` module +
Groovy engine JAR on the Connect image, which is a nontrivial extra
dependency to install and defend.

If the noise ever bothers you: install `debezium-scripting` on the Connect
image and add per-connector filters that check
`value.after.aggregate_type.startsWith('saga.')`. The connector configs are
otherwise unchanged.

## Registration

```bash
scripts/register-connectors.sh   # picks up both JSON files
```

## Teardown

Delete a connector via the Connect REST API — **that does not release the
Postgres replication slot**. Always drop the slot too or the WAL will grow
unbounded:

```sql
SELECT pg_drop_replication_slot('finflow_outbox_slot');
SELECT pg_drop_replication_slot('finflow_saga_outbox_slot');
```
