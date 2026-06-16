#!/usr/bin/env bash
# Proves the CDC pipeline end to end: insert one row into outbox_event, then
# consume the routed Kafka topic and watch the event arrive.

# This script simulates the exact job the aws-ingestor Java app will eventually do.
# It forcefully injects a single, fake billing event directly into the PostgreSQL database.
# Then, it immediately goes to the end of the pipeline (Kafka) and watches to see if that exact message comes cleanly out of the pipe.

# IMPORTANT: run this only AFTER the connector shows state RUNNING (snapshot.mode
# is schema_only, so only rows inserted while the connector is live are streamed).
set -euo pipefail


# Sets up the exact names of your Docker containers and Kafka topics.
PG_CONTAINER="${PG_CONTAINER:-finflow-postgres}"      # postgres container name
KAFKA_CONTAINER="${KAFKA_CONTAINER:-finflow-kafka}"   # kafka container name
BROKER="${BROKER:-kafka:9092}"                        # kafka internal listener
TOPIC="${TOPIC:-finflow.events.billing}"
# apache/kafka ships the scripts under /opt/kafka/bin but doesn't put them on PATH.
# Confluent images: kafka-console-consumer  | Bitnami/Apache: kafka-console-consumer.sh
CONSUMER_BIN="${CONSUMER_BIN:-/opt/kafka/bin/kafka-console-consumer.sh}"


# docker exec -i jumps inside your running PostgreSQL container.
# psql ... <<'SQL' opens the Postgres command-line tool and feeds it the block of text between <<'SQL' and SQL
# We pin a FIXED id so every run re-emits the SAME event id. The outbox table's
# primary key forbids inserting that id twice, so we DELETE the row first (a no-op
# on the first run). The delete is silent to Kafka — the Debezium EventRouter only
# routes inserts, and tombstones.on.delete is false — so each run produces exactly
# one CDC event carrying id=1111...  On the FIRST run the consumer processes it; on
# EVERY LATER run the consumer finds id=1111... already in its processed_event
# ledger and logs "Skipping already-processed event id=...".
echo "› Inserting a test outbox_event row (aggregate_type=billing) into '$PG_CONTAINER' ..."
docker exec -i "$PG_CONTAINER" psql -U finflow -d finflow -v ON_ERROR_STOP=1 <<'SQL'
DELETE FROM public.outbox_event WHERE id = '11111111-1111-1111-1111-111111111111';
INSERT INTO public.outbox_event (id, aggregate_type, aggregate_id, type, payload, created_at)
VALUES ('11111111-1111-1111-1111-111111111111', 'billing', 'aws-cur-demo',
          'RawBillingPagePulled',
          '{"source":"aws-cur","items":[]}', now());
SQL
echo "  inserted."


# Jumps inside your running Kafka container and executes the native kafka-console-consumer.sh tool
# to read messages directly from the command line.
echo "› Consuming '$TOPIC' from the beginning (Ctrl-C to stop) ..."
echo "  expect: key = aws-cur-demo  |  value = the JSON payload"
# --from-beginning:        read from the very first message in the topic, not just new ones.
# --property print.key:    print the key (aggregate_id) alongside the payload; the CLI prints only the value otherwise.
# --property key.separator: how to separate key and value in the printed output.
docker exec -it "$KAFKA_CONTAINER" "$CONSUMER_BIN" \
  --bootstrap-server "$BROKER" \
  --topic "$TOPIC" \
  --from-beginning \
  --property print.key=true \
  --property key.separator=" | "