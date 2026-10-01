#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
OUT=build/security-cloud-tests
mkdir -p "$OUT"
mapfile -t STUBS < <(find tests/security/cloud-stubs -name '*.java')
javac --release 8 -Xlint:-options -encoding UTF-8 -cp vendor/json-desktop.jar -d "$OUT" \
  "${STUBS[@]}" src/com/example/tingxiejian/Cloud.java tests/security/CloudSnapshotCheck.java
java -cp "$OUT:vendor/json-desktop.jar" com.example.tingxiejian.CloudSnapshotCheck
