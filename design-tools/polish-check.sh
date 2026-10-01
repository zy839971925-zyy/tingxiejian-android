#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
test -s vendor/json-desktop.jar || { echo 'Missing vendor/json-desktop.jar' >&2; exit 1; }
OUT=build/polish-check
rm -rf "$OUT"
mkdir -p "$OUT"
javac -encoding UTF-8 -cp vendor/json-desktop.jar -d "$OUT" \
  design-tools/polish-stubs/android/content/Context.java \
  design-tools/polish-stubs/android/util/AtomicFile.java \
  design-tools/polish-stubs/com/example/tingxiejian/Cloud.java \
  src/com/example/tingxiejian/History.java \
  src/com/example/tingxiejian/Exporter.java \
  src/com/example/tingxiejian/PolishValidator.java \
  src/com/example/tingxiejian/TranscriptPolisher.java \
  design-tools/PolishCheck.java
java -cp "$OUT:vendor/json-desktop.jar" com.example.tingxiejian.PolishCheck
