#!/usr/bin/env bash
# Registers (or re-registers) the FinFlow Debezium connectors with Kafka Connect.
# Idempotent: deletes an existing connector of the same name first, then posts.

set -euo pipefail  #bash strict mode - if any line fails, stop the script and exit

CONNECT_URL="${CONNECT_URL:-http://localhost:8083}"  #local kafka connect port
HERE="$(cd "$(dirname "$0")" && pwd)"                # command to know in which folder this script is in
CONNECTOR_FILE="$HERE/../infra/debezium/outbox-connector.json"  #absolute path of this json file

have_jq() { command -v jq >/dev/null 2>&1; }
pretty() { if have_jq; then jq .; else cat; fi; }


# waits for kafka connect to boot up fully after we start docker.
# This until loop acts like a ping. It knocks on the door (curl) every 2 seconds,
# prints a dot (.), and waits until it finally gets a successful response before moving on.
echo "› Waiting for Kafka Connect at $CONNECT_URL ..."
until curl -fsS "$CONNECT_URL/connectors" >/dev/null 2>&1; do
  printf '.'; sleep 2;
done
echo " up."

# uses grep & sed (text search tools) to physically open JSON file, read the "name" field, & pull the value out dynamically.
NAME="$(grep -o '"name"[[:space:]]*:[[:space:]]*"[^"]*"' "$CONNECTOR_FILE" \
        | head -1 | sed 's/.*"\([^"]*\)"$/\1/')"

# Idempotency - removes existing container if there by the same name
echo "› Removing any existing connector named '$NAME' (ignored if absent) ..."
curl -fsS -X DELETE "$CONNECT_URL/connectors/$NAME" >/dev/null 2>&1 || true
sleep 1

# installation
# It sends an HTTP POST request to Kafka Connect.
# The --data @"$CONNECTOR_FILE" flag attaches your JSON file as the body of the request.
echo "› Registering '$NAME' ..."
curl -fsS -X POST -H "Content-Type: application/json" \
     --data @"$CONNECTOR_FILE" "$CONNECT_URL/connectors" | pretty

# verification - if Kafka Connect is running smoothly or has it crashed
sleep 2
echo "› Status:"
curl -fsS "$CONNECT_URL/connectors/$NAME/status" | pretty

echo "› Done. List all connectors:  curl -s $CONNECT_URL/connectors | ${have_jq:+jq .}"