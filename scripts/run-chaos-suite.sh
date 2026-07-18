#!/usr/bin/env bash
#
# Day 25 — one command to run the chaos suite.
#
#   ./scripts/run-chaos-suite.sh
#
# What it does, and why each step is not optional:
#
#   1. Brings up infra WITH the chaos overlay. The overlay adds Kafka's PROXY
#      listener (advertised as localhost:26092). Without it, clients that connect
#      through Toxiproxy get told "the broker is at kafka:9092", dial that directly,
#      and bypass the proxy — so your toxics silently do nothing and the scenario
#      passes for the wrong reason.
#
#   2. Recreates toxiproxy. Its proxy list is read from toxiproxy.json AT BOOT.
#      That file was an empty [] until today; if you don't recreate the container it
#      keeps serving the old empty config and preflight will (correctly) fail.
#
#   3. Registers the Debezium connectors if they're missing. No Debezium, no outbox
#      -> Kafka, and every single saga hangs in STARTED. Failing loudly here beats
#      spending an hour debugging "why is nothing moving".
#
#   4. Builds the boot jars. The harness runs services as `java -jar` because it has
#      to be able to SIGKILL and restart them (see ServiceProcess).
#
#   5. Runs chaosTest.
#
# NOTE: stop any `./gradlew bootRun` instances first — the harness starts its own
# copies of chaos-api / saga-orchestrator / aws-adapter-worker and will collide on
# ports 9000 / 8088 / 8089.

set -euo pipefail
cd "$(dirname "$0")/.."

COMPOSE="docker compose -f docker-compose.yml -f docker-compose.chaos.yml"

echo "==> 1/5  infra up (with chaos overlay: Kafka PROXY listener)"
$COMPOSE up -d postgres kafka connect toxiproxy

echo "==> 2/5  recreate toxiproxy so it re-reads toxiproxy.json"
$COMPOSE up -d --force-recreate toxiproxy
until curl -sf localhost:8474/version >/dev/null 2>&1; do sleep 1; done
if ! curl -sf localhost:8474/proxies/kafka >/dev/null 2>&1; then
  echo "    !! toxiproxy has no 'kafka' proxy — check infra/docker/toxiproxy/toxiproxy.json"
  exit 1
fi
echo "    kafka proxy is live: localhost:26092 -> kafka:29192"

echo "==> 3/5  Debezium connectors"
until curl -sf localhost:8083/connectors >/dev/null 2>&1; do sleep 2; done
for c in outbox-connector saga-outbox-connector; do
  name=$(python3 -c "import json;print(json.load(open('infra/debezium/$c.json'))['name'])")
  if curl -sf "localhost:8083/connectors/$name" >/dev/null 2>&1; then
    echo "    $name already registered"
  else
    echo "    registering $name"
    curl -sf -X POST -H 'Content-Type: application/json' \
         --data @"infra/debezium/$c.json" localhost:8083/connectors >/dev/null
  fi
done

echo "==> 4/5  building boot jars"
./gradlew bootJar -q

echo "==> 5/5  running the chaos suite (this takes a while — 4 scenarios x 20 sagas)"
./gradlew :tests:chaos-suite:chaosTest

echo
echo "service logs from the run: build/chaos-logs/*.log"
