#!/usr/bin/env bash
set -euo pipefail

# ------------------------------------------------------------------------------
# FinFlow — Week 1 end-to-end smoke test.
#
# Prerequisites (start these first, in separate terminals):
#   1. infra:        docker compose up -d
#   2. chaos-api:    ./gradlew :services:chaos-api:bootRun
#   3. aws-ingestor: ./gradlew :services:aws-ingestor:bootRun
#
# This script disables chaos for deterministic checks, exercises every Week-1
# surface (infra, both mock clouds, the ingestor's DB + Kafka), then restores
# chaos. Self-contained: the Kafka round-trip is driven entirely over HTTP.
# ------------------------------------------------------------------------------

CHAOS="http://localhost:9000"
INGEST="http://localhost:8081"
PASS=0 ; FAIL=0

ok()  { echo "  [PASS] $1"; PASS=$((PASS+1)); }
bad() { echo "  [FAIL] $1"; FAIL=$((FAIL+1)); }
req() { curl -fsS --max-time 10 "$@"; }
jget(){ python3 -c "import sys,json;print($1)"; }

echo "==> FinFlow Week 1 smoke test"

# 1. infra: Postgres
if docker compose exec -T postgres pg_isready -U finflow >/dev/null 2>&1; then
  ok "Postgres reachable"
else
  bad "Postgres not reachable (run: docker compose up -d)"
fi

# 2. chaos-api health
if req "$CHAOS/actuator/health" | grep -q "UP"; then ok "chaos-api UP"; else bad "chaos-api not UP"; fi

# 3. aws-ingestor health
if req "$INGEST/actuator/health" | grep -q "UP"; then ok "aws-ingestor UP"; else bad "aws-ingestor not UP"; fi

# 4. disable chaos so the data checks are deterministic
if req -X POST "$CHAOS/chaos/enable?on=false" >/dev/null; then ok "chaos disabled for smoke"; else bad "could not disable chaos"; fi

# 5. AWS mock returns CUR line items
if AWS_COUNT=$(req "$CHAOS/aws/cost-and-usage-report" | jget 'len(json.load(sys.stdin)["lineItems"])') && [ "$AWS_COUNT" -ge 1 ]; then
  ok "AWS CUR returns ${AWS_COUNT} items"
else
  bad "AWS CUR returned no items"
fi

# 6. GCP mock returns billing rows
if GCP_COUNT=$(req "$CHAOS/gcp/billing-export" | jget 'len(json.load(sys.stdin)["rows"])') && [ "$GCP_COUNT" -ge 1 ]; then
  ok "GCP export returns ${GCP_COUNT} rows"
else
  bad "GCP export returned no rows"
fi

# 7. ingestor Kafka round-trip + Postgres write (one call proves both)
if RT=$(req -X POST "$INGEST/internal/smoke/publish?payload=week1" | jget 'json.load(sys.stdin)["roundTripMillis"]'); then
  ok "Kafka produce->consume round-trip OK (${RT}ms)"
else
  bad "Kafka round-trip failed"
fi

# 8. Postgres cursor row was written (Flyway table + JPA)
if CUR=$(req "$INGEST/internal/smoke/cursor" | jget 'len(json.load(sys.stdin))') && [ "$CUR" -ge 1 ]; then
  ok "poll_cursor row present (${CUR})"
else
  bad "poll_cursor empty (Flyway/JPA problem)"
fi

# 9. restore chaos to its configured default
if req -X POST "$CHAOS/chaos/enable?on=true" >/dev/null; then ok "chaos re-enabled"; else bad "could not re-enable chaos"; fi

echo
echo "  ${PASS} passed, ${FAIL} failed"
if [ "$FAIL" -eq 0 ]; then
  echo "==> Week 1 smoke PASSED"
else
  echo "==> Week 1 smoke FAILED"
  exit 1
fi
