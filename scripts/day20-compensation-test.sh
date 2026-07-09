#!/usr/bin/env bash
#
# Day 20 compensation test — the plan's "chaos 503 on step 3 → COMPENSATED"
# scenario. Uses the new per-endpoint chaos targeting (chaos-api Day 20).

set -euo pipefail
BASE="${BASE:-http://localhost:8088}"
CHAOS="${CHAOS:-http://localhost:9000}"
CORR="comp-$(date +%s)"

echo "1/5  Configuring chaos: 100% failure on /aws/commitments/*/reserve"
curl -sS -X POST "$CHAOS/chaos/enabled?value=true" >/dev/null
curl -sS -X POST "$CHAOS/chaos/fault-rate?value=0" >/dev/null
curl -sS -X POST "$CHAOS/chaos/target-path?value=reserve" >/dev/null
curl -sS -X POST "$CHAOS/chaos/target-rate?value=100" >/dev/null
curl -sS "$CHAOS/chaos" | python3 -m json.tool

echo "2/5  Starting saga (correlation=$CORR)"
RESP=$(curl -sS -X POST "$BASE/sagas/rebalance" \
  -H 'Content-Type: application/json' \
  -d "{\"correlationId\":\"$CORR\",\"vendor\":\"AWS\"}")
SAGA_ID=$(echo "$RESP" | python3 -c "import json,sys;print(json.load(sys.stdin)['id'])")
echo "     saga-id=$SAGA_ID"

echo "3/5  Waiting for COMPENSATED..."
for i in {1..60}; do
  STATE=$(curl -sS "$BASE/sagas/$SAGA_ID" | python3 -c "import json,sys;print(json.load(sys.stdin)['currentState'])")
  COMPLETED=$(curl -sS "$BASE/sagas/$SAGA_ID" | python3 -c "import json,sys;print(json.load(sys.stdin)['completedSteps'])")
  echo "     [$i] state=$STATE completed=$COMPLETED"
  case "$STATE" in
    COMPENSATED)          echo "PASS"; RESULT=0; break ;;
    COMPENSATION_FAILED)  echo "PARTIAL: reached COMPENSATION_FAILED — an Undo itself failed"; RESULT=1; break ;;
    COMPLETED)            echo "FAIL: reached COMPLETED, but expected COMPENSATED"; RESULT=1; break ;;
  esac
  sleep 2
done

echo "4/5  Restoring chaos to defaults"
curl -sS -X POST "$CHAOS/chaos/target-clear" >/dev/null
curl -sS -X POST "$CHAOS/chaos/fault-rate?value=20" >/dev/null

echo "5/5  Result:"
if [ "${RESULT:-1}" -eq 0 ]; then
  echo "SUCCESS — saga $SAGA_ID reached COMPENSATED as expected."
  exit 0
fi
echo "FAIL — see state above."
exit 1
