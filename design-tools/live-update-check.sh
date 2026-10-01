#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
out=build/live-update-check
mkdir -p "$out"
mapfile -t stubs < <(rg --files design-tools/live-update-stubs -g '*.java')
javac --release 8 -Xlint:-options -encoding UTF-8 -d "$out" "${stubs[@]}" \
  src/com/example/tingxiejian/{Job,NotificationUpdateGate,AndroidLiveUpdateCapability,AndroidLiveUpdatePublisher}.java \
  design-tools/LiveUpdateCheck.java
java -cp "$out" com.example.tingxiejian.LiveUpdateCheck
