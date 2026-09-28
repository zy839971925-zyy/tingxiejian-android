#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
test -s vendor/json-desktop.jar || { echo 'Missing vendor/json-desktop.jar; see docs/BUILDING.md' >&2; exit 1; }
OUT=build/export-check
rm -rf "$OUT"; mkdir -p "$OUT"
javac -encoding UTF-8 -cp vendor/json-desktop.jar -d "$OUT" \
  design-tools/stubs/com/example/tingxiejian/History.java \
  src/com/example/tingxiejian/Exporter.java \
  design-tools/ExportCheck.java
java -cp "$OUT:vendor/json-desktop.jar" com.example.tingxiejian.ExportCheck
