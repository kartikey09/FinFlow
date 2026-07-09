#!/usr/bin/env bash
#
# Day 20 outbox-starter refactor cleanup.
#
# The library now owns JpaConfig / OutboxFlywayConfig / orm.xml. Each service's
# copies of these files are now redundant. This script removes them.
#
# WHAT IT DOES: rm -f on 3 files across 7 services (21 files total).
# WHAT IT DOESN'T DO: rebuild, test, git commit. Do those yourself after inspecting the diff.
#
# SAFE TO RUN MULTIPLE TIMES.

set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"

removed=0
for svc in aws-ingestor gcp-ingestor cost-normalizer commitment-tracker \
           saga-orchestrator aws-adapter-worker gcp-adapter-worker; do
  pkg=""
  case "$svc" in
    aws-ingestor)           pkg="io/finflow/ingestion/aws/config" ;;
    gcp-ingestor)           pkg="io/finflow/ingestion/gcp/config" ;;
    cost-normalizer)        pkg="io/finflow/normalizer/config" ;;
    commitment-tracker)     pkg="io/finflow/commitment/config" ;;
    saga-orchestrator)      pkg="io/finflow/saga/config" ;;
    aws-adapter-worker)     pkg="io/finflow/adapter/aws/config" ;;
    gcp-adapter-worker)     pkg="io/finflow/adapter/gcp/config" ;;
  esac

  base="$ROOT/services/$svc/src/main"
  for f in "$base/java/$pkg/JpaConfig.java" "$base/java/$pkg/OutboxFlywayConfig.java" \
           "$base/resources/META-INF/orm.xml"; do
    if [ -f "$f" ]; then
      echo "  removing $f"
      rm -f "$f"
      removed=$((removed + 1))
    fi
  done
done

echo ""
echo "Removed $removed files. Now:"
echo "  1. ./gradlew clean build         (confirms nothing's broken)"
echo "  2. git diff --stat               (should show ~21 file deletions)"
echo "  3. Run the tests you care about."
