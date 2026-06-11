#!/usr/bin/env bash

# Layered verification of the Day 7 Debezium CDC pipeline.
# Each check is independent, so a failure pinpoints the broken layer.
# Safe to re-run: it only inserts harmless probe rows (clean up with
#   DELETE FROM outbox_event WHERE aggregate_id LIKE 'probe-%';
# NOTE: deliberately NOT 'set -e' — we want every check to run and report.
set -uo pipefail

# --- adjust these to match your compose if needed -------------------------
CONNECT_URL="${CONNECT_URL:-http://localhost:8083}"
CONNECTOR="${CONNECTOR:-finflow-outbox-connector}"
SLOT="${SLOT:-finflow_outbox_slot}"
PUBLICATION="${PUBLICATION:-finflow_outbox_pub}"
PG_CONTAINER="${PG_CONTAINER:-finflow-postgres}"
KAFKA_CONTAINER="${KAFKA_CONTAINER:-finflow-kafka}"
BROKER="${BROKER:-kafka:9092}"      # if this fails inside the container, try localhost:9092
PGUSER="${PGUSER:-finflow}"
PGDB="${PGDB:-finflow}"
# --------------------------------------------------------------------------

PASS=0; FAIL=0
ok()   { printf "  \033[32mPASS\033[0m  %s\n" "$1"; PASS=$((PASS+1)); }
no()   { printf "  \033[31mFAIL\033[0m  %s\n" "$1"; FAIL=$((FAIL+1)); }
info() { printf "  ····  %s\n" "$1"; }

have_jq(){ command -v jq >/dev/null 2>&1; }
psql_q(){ docker exec -i "$PG_CONTAINER" psql -U "$PGUSER" -d "$PGDB" -tAc "$1" 2>/dev/null; }

echo "FinFlow · Day 7 CDC pipeline verification"
echo "=========================================="

# [1] Kafka Connect REST API reachable -------------------------------------
echo "[1] Kafka Connect REST API"
if curl -fsS "$CONNECT_URL/" >/dev/null 2>&1; then
  ok "Connect reachable at $CONNECT_URL"
else
  no "Connect NOT reachable at $CONNECT_URL — is the 'connect' container up?"
fi

# [2] Connector is registered ----------------------------------------------
echo "[2] Connector registration"
LIST=$(curl -fsS "$CONNECT_URL/connectors" 2>/dev/null || echo "")
if echo "$LIST" | grep -q "$CONNECTOR"; then
  ok "connector '$CONNECTOR' is registered"
else
  no "connector '$CONNECTOR' not found — run ./scripts/register-connectors.sh"
fi

# [3] Connector + task are RUNNING (not FAILED) ----------------------------
echo "[3] Connector health"
STATUS=$(curl -fsS "$CONNECT_URL/connectors/$CONNECTOR/status" 2>/dev/null || echo "")
if have_jq && [ -n "$STATUS" ]; then
  CST=$(echo "$STATUS" | jq -r '.connector.state // "MISSING"')
  TST=$(echo "$STATUS" | jq -r '.tasks[0].state // "MISSING"')
else
  CST=$(echo "$STATUS" | grep -o '"state":"[A-Z]*"' | head -1 | sed 's/.*:"//;s/"//')
  TST=$(echo "$STATUS" | grep -o '"state":"[A-Z]*"' | sed -n '2p' | sed 's/.*:"//;s/"//')
fi
[ "$CST" = "RUNNING" ] && ok "connector state = RUNNING" || no "connector state = ${CST:-unknown}"
[ "$TST" = "RUNNING" ] && ok "task[0] state = RUNNING" \
                       || no "task state = ${TST:-unknown}  (GET $CONNECT_URL/connectors/$CONNECTOR/status for the trace)"

# [4] Postgres wal_level is logical ----------------------------------------
echo "[4] Postgres wal_level"
WAL=$(psql_q "SHOW wal_level;")
[ "$WAL" = "logical" ] && ok "wal_level = logical" || no "wal_level = ${WAL:-unreachable} (must be 'logical')"

# [5] Replication slot exists and is active --------------------------------
echo "[5] Replication slot"
SLOT_ACTIVE=$(psql_q "SELECT active FROM pg_replication_slots WHERE slot_name='$SLOT';")
if   [ "$SLOT_ACTIVE" = "t" ]; then ok "slot '$SLOT' exists and is active"
elif [ "$SLOT_ACTIVE" = "f" ]; then no "slot '$SLOT' exists but is INACTIVE (connector not consuming)"
else no "slot '$SLOT' does not exist"; fi

# [6] Publication includes the outbox table --------------------------------
echo "[6] Publication"
PUB=$(psql_q "SELECT 1 FROM pg_publication_tables WHERE pubname='$PUBLICATION' AND tablename='outbox_event';")
[ "$PUB" = "1" ] && ok "publication '$PUBLICATION' includes outbox_event" \
                 || no "publication '$PUBLICATION' missing or does not include outbox_event"

# locate the console-consumer binary (name differs across Kafka images) -----
CONSUMER_BIN=$(docker exec "$KAFKA_CONTAINER" sh -c \
  'command -v kafka-console-consumer.sh || command -v kafka-console-consumer' 2>/dev/null | head -1)

consume(){ # $1 = topic ; prints messages then exits after 12s idle
  docker exec "$KAFKA_CONTAINER" "$CONSUMER_BIN" --bootstrap-server "$BROKER" \
    --topic "$1" --from-beginning --timeout-ms 12000 \
    --property print.key=true --property print.headers=true \
    --property key.separator=' | ' 2>/dev/null
}

if [ -z "$CONSUMER_BIN" ]; then
  info "kafka console consumer not found in '$KAFKA_CONTAINER' — skipping flow checks [7][8]"
  info "use Kafka UI (http://localhost:8090) to watch topics instead"
else
  # [7] End-to-end: a 'billing' row reaches finflow.events.billing ---------
  echo "[7] End-to-end flow  (aggregate_type=billing)"
  M1="probe-billing-$(date +%s)-$RANDOM"
  psql_q "INSERT INTO ingestion.outbox_event (id,aggregate_type,aggregate_id,type,payload,created_at)
          VALUES (gen_random_uuid(),'billing','$M1','RawBillingPagePulled','{\"marker\":\"$M1\"}',now());" >/dev/null
  info "inserted marker=$M1; giving Debezium a moment, then consuming finflow.events.billing ..."
  sleep 5
  OUT1=$(consume "finflow.events.billing")
  if echo "$OUT1" | grep -q "$M1"; then
    ok "event arrived on finflow.events.billing (value contains the marker)"
    echo "$OUT1" | grep -q 'id:' && ok "message carries an 'id' header (the dedup key for Day 9)" \
                                  || info "no 'id' header detected in console output (verify via Kafka UI)"
    echo "$OUT1" | grep -q "$M1" && echo "$OUT1" | grep -q "$M1" >/dev/null
  else
    no "marker not seen on finflow.events.billing within timeout"
  fi

  # [8] Dynamic routing: a 'commitment' row reaches a DIFFERENT topic ------
  echo "[8] Dynamic routing  (aggregate_type=commitment)"
  M2="probe-commit-$(date +%s)-$RANDOM"
  psql_q "INSERT INTO ingestion.outbox_event (id,aggregate_type,aggregate_id,type,payload,created_at)
          VALUES (gen_random_uuid(),'commitment','$M2','CommitmentRecorded','{\"marker\":\"$M2\"}',now());" >/dev/null
  info "inserted marker=$M2; consuming finflow.events.commitment ..."
  sleep 5
  OUT2=$(consume "finflow.events.commitment")
  echo "$OUT2" | grep -q "$M2" && ok "event routed to a SEPARATE topic finflow.events.commitment" \
                               || no "marker not seen on finflow.events.commitment (is route.by.field working?)"
  # leakage cross-check: the commitment marker must NOT appear on the billing topic
  OUT1B=$(consume "finflow.events.billing")
  echo "$OUT1B" | grep -q "$M2" && no "commitment event leaked onto the billing topic" \
                                || ok "no cross-topic leakage (routing is genuinely per aggregate_type)"
fi

echo "=========================================="
echo "Summary: $PASS passed, $FAIL failed"
if [ "$FAIL" -eq 0 ]; then echo "Day 7 pipeline: HEALTHY ✓"; exit 0; else echo "Day 7 pipeline: see failures above"; exit 1; fi
