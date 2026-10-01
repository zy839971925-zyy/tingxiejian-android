#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
UI_MOTION_OUT=build/ui-motion-check
mkdir -p "$UI_MOTION_OUT"
mapfile -t UI_MOTION_STUBS < <(rg --files design-tools/ui-stubs -g '*.java')
javac --release 8 -encoding UTF-8 -d "$UI_MOTION_OUT" "${UI_MOTION_STUBS[@]}" src/com/example/tingxiejian/{SpringCurve,Motion,UiTheme}.java design-tools/UiMotionCheck.java
java -cp "$UI_MOTION_OUT" com.example.tingxiejian.UiMotionCheck
javac --release 8 -encoding UTF-8 -cp vendor/json-desktop.jar -d "$UI_MOTION_OUT" design-tools/stubs/com/example/tingxiejian/History.java src/com/example/tingxiejian/{Exporter,TranscriptSearch}.java design-tools/{ExportMenuCheck,TranscriptSearchCheck}.java
java -cp "$UI_MOTION_OUT:vendor/json-desktop.jar" com.example.tingxiejian.ExportMenuCheck
java -cp "$UI_MOTION_OUT" com.example.tingxiejian.TranscriptSearchCheck
