#!/usr/bin/env bash
#
# FinFlow · manually inject a saga.events record while adapter workers don't
# exist yet (Days 18–19).
#
# Usage:
#   scripts/inject-saga-event.sh <sagaId> <step> <success> [reason]
#
# Examples:
#   # Simulate ACQUIRE_LOCK succeeding for saga 8f...:
#   scripts/inject-saga-event.sh 8f0a4a4c-... ACQUIRE_LOCK true
#
#   # Simulate a Chaos-API-injected failure on RESERVE_TARGET:
#   scripts/inject-saga-event.sh 8f0a4a4c-... RESERVE_TARGET false "Chaos 503"
#
# The record is published to `saga.events` with:
#   - key   = sagaId
#   - value = { "sagaId": "...", "step": "...", "success": true/false, "reason": "..." }
#
# Uses docker exec on the kafka container's kafka-console-producer, matching
# scripts/verify-cdc.sh's pattern.

set -uo pipefail
if [ $# -lt 3 ]; then
  echo "usage: $0 <sagaId> <step> <success> [reason]" >&2
  exit 2
fi

SAGA_ID="$1"
STEP="$2"
SUCCESS="$3"
REASON="${4:-}"

KAFKA_SERVICE="${KAFKA_SERVICE:-kafka}"

if docker compose version >/dev/null 2>&1; then DC="docker compose"
elif command -v docker-compose >/dev/null 2>&1; then DC="docker-compose"
else echo "ERROR: docker compose not found." >&2; exit 2; fi

PAYLOAD=$(python3 -c "
import json,sys
print(json.dumps({
  'sagaId': '$SAGA_ID',
  'step': '$STEP',
  'success': '$SUCCESS'.lower() == 'true',
  'reason': '$REASON' or None,
}))")

echo "publishing to saga.events:"
echo "  key   = $SAGA_ID"
echo "  value = $PAYLOAD"

$DC exec -T "$KAFKA_SERVICE" bash -lc \
  "echo '${SAGA_ID}:${PAYLOAD}' | /opt/kafka/bin/kafka-console-producer.sh \
      --bootstrap-server localhost:9092 --topic saga.events \
      --property parse.key=true --property key.separator=:"

echo "done."
