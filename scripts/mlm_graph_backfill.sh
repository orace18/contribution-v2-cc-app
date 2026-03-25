#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

echo "Running MLM Neo4j graph backfill from PostgreSQL source of truth..."

mvn -q spring-boot:run \
  -Dspring-boot.run.arguments="--spring.main.web-application-type=none --mlm.graph.enabled=true --mlm.graph.backfill.enabled=true --mlm.graph.backfill.prune-orphans=true --mlm.graph.backfill.fail-fast=true"

echo "MLM Neo4j graph backfill completed."

