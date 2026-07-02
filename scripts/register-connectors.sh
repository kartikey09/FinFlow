#!/usr/bin/env bash
# Register every Debezium connector under infra/debezium/*.json.
# Idempotent — an already-registered connector name gets a PUT (update config)
# instead of a POST (create). Safe to run repeatedly.

set -uo pipefail
CONNECT_URL="${CONNECT_URL:-http://localhost:8083}"
DIR="$(cd "$(dirname "$0")/.." && pwd)/infra/debezium"

register_one() {
  local json="$1"
  local name
  name=$(python3 -c "import json;print(json.load(open('$json'))['name'])")
  local body
  body=$(python3 -c "import json;print(json.dumps(json.load(open('$json'))['config']))")
  local code
  code=$(curl -s -o /dev/null -w '%{http_code}' "$CONNECT_URL/connectors/$name")
  if [ "$code" = "200" ]; then
    echo "  updating existing connector $name"
    curl -s -X PUT -H "Content-Type: application/json" \
      --data "$body" "$CONNECT_URL/connectors/$name/config" > /dev/null
  else
    echo "  registering new connector $name"
    curl -s -X POST -H "Content-Type: application/json" \
      --data @"$json" "$CONNECT_URL/connectors" > /dev/null
  fi
}

for f in "$DIR"/*.json; do
  register_one "$f"
done
echo "done."
