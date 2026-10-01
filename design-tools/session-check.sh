#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
SESSION_CHECK_OUT=build/session-check
mkdir -p "$SESSION_CHECK_OUT"
javac --release 8 -Xlint:-options -encoding UTF-8 -cp vendor/json-desktop.jar -d "$SESSION_CHECK_OUT" \
  design-tools/polish-stubs/android/content/Context.java \
  design-tools/polish-stubs/android/util/AtomicFile.java \
  src/com/example/tingxiejian/SessionState.java \
  src/com/example/tingxiejian/SessionLedger.java \
  src/com/example/tingxiejian/SessionRepository.java \
  src/com/example/tingxiejian/CloudTranscript.java \
  design-tools/SessionStateCheck.java \
  design-tools/SessionLedgerCheck.java \
  design-tools/SessionRepositoryCheck.java \
  design-tools/CloudTranscriptCheck.java
java -cp "$SESSION_CHECK_OUT" SessionStateCheck
java -cp "$SESSION_CHECK_OUT" SessionLedgerCheck
java -cp "$SESSION_CHECK_OUT" com.example.tingxiejian.SessionRepositoryCheck
java -cp "$SESSION_CHECK_OUT:vendor/json-desktop.jar" com.example.tingxiejian.CloudTranscriptCheck
