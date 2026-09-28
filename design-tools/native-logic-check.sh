#!/usr/bin/env bash
# Compiles and runs the shipped pure-Java logic on a desktop JDK: no Android, no device needed.
set -euo pipefail
cd "$(dirname "$0")/.."
OUT=build/logic-check
rm -rf "$OUT"; mkdir -p "$OUT"
javac -encoding UTF-8 -d "$OUT" \
  src/com/example/tingxiejian/Job.java \
  src/com/example/tingxiejian/SpringCurve.java \
  design-tools/NativeLogicCheck.java
java -cp "$OUT" NativeLogicCheck
