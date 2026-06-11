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
# Confluent images: kafka-console-consumer  | Bitnami/Apache: kafka-console-consumer.sh
CONSUMER_BIN="${CONSUMER_BIN:-kafka-console-consumer.sh}"


# docker exec -i jumps inside your running PostgreSQL container.
# psql ... <<'SQL' opens the Postgres command-line tool and feeds it the block of text between <<'SQL' and SQL
echo "› Inserting a test outbox_event row (aggregate_type=billing) into '$PG_CONTAINER' ..."
docker exec -i "$PG_CONTAINER" psql -U finflow -d finflow -v ON_ERROR_STOP=1 <<'SQL'
INSERT INTO public.outbox_event (id, aggregate_type, aggregate_id, type, payload, created_at)
VALUES (gen_random_uuid(),
        'billing',
        'aws-cur-demo',
        'RawBillingPagePulled',
        '{"source":"aws-cur","items":[{"sku":"p3.16xlarge","onDemandRate":24.48,"effectiveRate":10.20}]}',
        now());
SQL
echo "  inserted."


# Jumps inside your running Kafka container and executes the native kafka-console-consumer.sh tool
# to read messages directly from the command line.
echo "› Consuming '$TOPIC' from the beginning (Ctrl-C to stop) ..."
echo "  expect: key = aws-cur-demo  |  value = the JSON payload"
docker exec -it "$KAFKA_CONTAINER" "$CONSUMER_BIN" \
  --bootstrap-server "$BROKER" \
  --topic "$TOPIC" \
  --from-beginning \                 # Tells Kafka not just to listen for new messages, but start reading from the 1st msg ever put into this topic.
  --property print.key=true \        # prints key along with payload, kafka's default cli only prints the json payload otherwise.
  --property key.separator=" | "