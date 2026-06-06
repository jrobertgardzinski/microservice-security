#!/usr/bin/env bash
# Reverse-direction traceability: concept slug -> tests that exercise it.
# Reads the Allure results produced by `mvn test` and groups by the `concept` link.
#
# Usage:   scripts/concept-traceability.sh [allure-results-dir]
# Default: target/allure-results
# Requires: jq
set -euo pipefail

RESULTS_DIR="${1:-target/allure-results}"

if ! command -v jq >/dev/null 2>&1; then
  echo "jq is required but not installed" >&2
  exit 1
fi

shopt -s nullglob
files=("$RESULTS_DIR"/*-result.json)
if [ ${#files[@]} -eq 0 ]; then
  echo "No *-result.json found in '$RESULTS_DIR' — run 'mvn test' first." >&2
  exit 1
fi

echo "# Concept -> tests (traceability)"
echo

jq -r '
  (.fullName // .name) as $test
  | (.links // [])[]
  | select(.type == "concept")
  | "\(.name)\t\($test)"
' "${files[@]}" \
| sort -u \
| awk -F'\t' '
    $1 != prev { if (prev != "") print ""; print "## " $1; prev = $1 }
    { print "  - " $2 }
'
