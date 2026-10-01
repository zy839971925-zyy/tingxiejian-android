#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
out=build/live-update-check
mkdir -p "$out"
# ripgrep is not guaranteed on CI runners; fall back to find(1).
if command -v rg >/dev/null 2>&1; then
  mapfile -t stubs < <(rg --files design-tools/live-update-stubs -g '*.java')
else
  mapfile -t stubs < <(find design-tools/live-update-stubs -type f -name '*.java' | sort)
fi
javac --release 8 -Xlint:-options -encoding UTF-8 -d "$out" "${stubs[@]}" \
  src/com/example/tingxiejian/{Job,NotificationUpdateGate,AndroidLiveUpdateCapability,AndroidLiveUpdatePublisher}.java \
  design-tools/LiveUpdateCheck.java
java -cp "$out" com.example.tingxiejian.LiveUpdateCheck
