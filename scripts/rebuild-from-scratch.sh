#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

if find "src/main/resources/firebase" -maxdepth 1 -type f -name '*.json' -print -quit | grep -q .; then
  echo "Firebase credential JSON files must not exist under src/main/resources/firebase."
  echo "Use FIREBASE_CREDENTIALS_PATH or FIREBASE_CREDENTIALS_BASE64 at runtime instead."
  exit 1
fi

if [[ -n "${MVN_BIN:-}" ]]; then
  MVN_EXEC="$MVN_BIN"
elif command -v mvn >/dev/null 2>&1; then
  MVN_EXEC="mvn"
elif [[ -x "${ROOT_DIR}/../.tools/apache-maven-3.9.9/bin/mvn.cmd" ]]; then
  MVN_EXEC="${ROOT_DIR}/../.tools/apache-maven-3.9.9/bin/mvn.cmd"
else
  echo "No Maven executable found. Set MVN_BIN or install Maven."
  exit 1
fi

"$MVN_EXEC" clean verify "$@"
