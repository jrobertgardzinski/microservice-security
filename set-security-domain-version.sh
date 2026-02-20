#!/bin/bash

set -e

if [ -z "$1" ]; then
  echo "Usage: $0 <new-version>"
  echo "Example: $0 1.6.0"
  exit 1
fi

NEW_VERSION="$1"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

echo "Setting security-domain version to $NEW_VERSION"

# 1. Set version in security-domain/pom.xml
mvn -f "$SCRIPT_DIR/security-domain/pom.xml" versions:set \
  -DnewVersion="$NEW_VERSION" \
  -DgenerateBackupPoms=false

# 2. Update security-domain.version property in root pom
mvn -f "$SCRIPT_DIR/pom.xml" versions:set-property \
  -Dproperty=security-domain.version \
  -DnewVersion="$NEW_VERSION" \
  -DgenerateBackupPoms=false

echo "Done. security-domain version set to $NEW_VERSION"
