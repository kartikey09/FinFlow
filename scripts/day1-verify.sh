#!/usr/bin/env bash
# =============================================================================
# scripts/day1-verify.sh — Run this at the end of Day 1 to prove everything works.
#
# Usage: bash scripts/day1-verify.sh
#
# Pass = green checkmarks across all 8 checks.
# Fail = stop and fix before moving to Day 2.
# =============================================================================

set -u

GREEN='\033[0;32m'; RED='\033[0;31m'; YELLOW='\033[1;33m'; NC='\033[0m'
PASS=0; FAIL=0

check() {
  local name="$1"
  local cmd="$2"
  if eval "$cmd" > /tmp/day1-check.out 2>&1; then
    echo -e "${GREEN}✓${NC} $name"
    PASS=$((PASS+1))
  else
    echo -e "${RED}✗${NC} $name"
    echo "    Output:"
    sed 's/^/      /' /tmp/day1-check.out | head -5
    FAIL=$((FAIL+1))
  fi
}

echo "=== Day 1 verification ==="
echo ""

# 1. Prerequisites
check "Java 21 installed"             "java -version 2>&1 | grep -qE '\"21\\.|version \"21'"
check "Docker running"                "docker info > /dev/null"
check "Docker Compose v2 installed"   "docker compose version > /dev/null"

echo ""
echo "=== Infrastructure ==="

# 2. Containers up and healthy
check "Postgres container healthy"    "docker inspect --format='{{.State.Health.Status}}' finflow-postgres | grep -q healthy"
check "Kafka container healthy"       "docker inspect --format='{{.State.Health.Status}}' finflow-kafka | grep -q healthy"

# 3. Postgres reachable + critical config set
check "Postgres reachable"            "docker compose exec -T postgres psql -U finflow -d finflow -c 'SELECT 1;' > /dev/null"
check "Postgres wal_level=logical"    "docker compose exec -T postgres psql -U finflow -d finflow -tAc \"SHOW wal_level\" | grep -q logical"

# 4. Kafka reachable
check "Kafka topic list works"        "docker compose exec -T kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list > /dev/null"

echo ""
echo "=== Summary ==="
echo -e "${GREEN}Passed:${NC} $PASS"
echo -e "${RED}Failed:${NC} $FAIL"
echo ""

if [ "$FAIL" -eq 0 ]; then
  echo -e "${GREEN}Day 1 done. Commit, push, and rest.${NC}"
  echo ""
  echo "Try opening in your browser:"
  echo "  http://localhost:8090  (Kafka UI)"
  echo "  http://localhost:8091  (pgAdmin · dev@finflow.local / finflow)"
  exit 0
else
  echo -e "${RED}Fix the failing checks before moving to Day 2.${NC}"
  exit 1
fi
