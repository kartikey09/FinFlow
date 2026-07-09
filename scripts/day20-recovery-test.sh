#!/usr/bin/env bash
#
# Day 20 recovery test — the plan's "docker kill saga-orchestrator mid-saga,
# docker start, assert it completes correctly" scenario.
#
# PREREQ:
#   - docker compose up -d (kafka, postgres, connect all healthy)
#   - scripts/register-connectors.sh
#   - chaos-api running (either bootRun locally or in docker compose)
#   - aws-adapter-worker running
#   - saga-orchestrator running IN DOCKER (so we can kill it)
#     If you're running it via ./gradlew :services:saga-orchestrator:bootRun,
#     this script's docker-kill won't work. See docker-compose overlay below.
#
# WHAT THIS TESTS:
#   Everything the orchestrator needs to resume lives on the SagaInstance row.
#   Killing the process mid-saga and restarting it must not lose forward
#   progress: the next Kafka event replay drives it to the correct next state.
#
# HOW:
#   1. Kick off a saga (chaos off, so the happy path).
#   2. Poll until currentState = LOCKED or later (i.e., step 1 done).
#   3. docker kill saga-orchestrator.
#   4. Sleep 3s.
#   5. docker start saga-orchestrator (wait for it to come up).
#   6. Poll until currentState = COMPLETED.

set -euo pipefail
CONTAINER="${SAGA_CONTAINER:-saga-orchestrator}"
BASE="${BASE:-http://localhost:8088}"
CHAOS="${CHAOS:-http://localhost:9000}"
CORR="rec-$(date +%s)"

echo "1/6  Setting chaos off (happy path only for this test)"
curl -sS -X POST "$CHAOS/chaos/enabled?value=false" >/dev/null

echo "2/6  Starting saga (correlation=$CORR)"
RESP=$(curl -sS -X POST "$BASE/sagas/rebalance" \
  -H 'Content-Type: application/json' \
  -d "{\"correlationId\":\"$CORR\",\"vendor\":\"AWS\"}")
SAGA_ID=$(echo "$RESP" | python3 -c "import json,sys;print(json.load(sys.stdin)['id'])")
echo "     saga-id=$SAGA_ID"

echo "3/6  Waiting for saga to reach at least LOCKED..."
for i in {1..30}; do
  STATE=$(curl -sS "$BASE/sagas/$SAGA_ID" | python3 -c "import json,sys;print(json.load(sys.stdin)['currentState'])")
  echo "     [$i] state=$STATE"
  case "$STATE" in
    LOCKED|VERIFIED|TARGET_RESERVED|SOURCE_RELEASED|LEDGER_UPDATED|COMPLETED) break ;;
  esac
  sleep 1
done

if [ "$STATE" = "COMPLETED" ]; then
  echo "     WARNING: saga completed before we could kill the orchestrator."
  echo "     This test isn't meaningful; either the pipeline is very fast or the"
  echo "     adapter workers are catching up too quickly. Try slowing them with"
  echo "     chaos-api hangs, or run without chaos-api's happy-path bypass."
  exit 0
fi

echo "4/6  docker kill $CONTAINER"
docker kill "$CONTAINER" || { echo "kill failed — is the orchestrator running in docker?"; exit 1; }
sleep 3

echo "5/6  docker start $CONTAINER"
docker start "$CONTAINER"
# Give Spring Boot a moment to come back up + reconnect Kafka.
sleep 15

echo "6/6  Waiting for saga to complete..."
for i in {1..60}; do
  STATE=$(curl -sS "$BASE/sagas/$SAGA_ID" | python3 -c "import json,sys;print(json.load(sys.stdin)['currentState'])")
  echo "     [$i] state=$STATE"
  if [ "$STATE" = "COMPLETED" ]; then
    echo "SUCCESS: saga survived docker kill and reached COMPLETED"
    exit 0
  fi
  sleep 2
done

echo "FAIL: saga did not reach COMPLETED within timeout"
echo "  final state: $STATE"
echo "  saga-id:     $SAGA_ID"
exit 1
