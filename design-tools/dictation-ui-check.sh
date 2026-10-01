#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
DICTATION_UI_OUT=build/dictation-ui-check
mkdir -p "$DICTATION_UI_OUT"
# ripgrep is not guaranteed on CI runners; fall back to find(1).
if command -v rg >/dev/null 2>&1; then
  mapfile -t DICTATION_STUB_SOURCES < <(rg --files design-tools/dictation-stubs -g '*.java')
else
  mapfile -t DICTATION_STUB_SOURCES < <(find design-tools/dictation-stubs -type f -name '*.java' | sort)
fi
javac --release 8 -encoding UTF-8 -cp vendor/json-desktop.jar -d "$DICTATION_UI_OUT" \
  "${DICTATION_STUB_SOURCES[@]}" \
  src/com/example/tingxiejian/DictationActivity.java \
  design-tools/DictationUiCheck.java
java -cp "$DICTATION_UI_OUT:vendor/json-desktop.jar" com.example.tingxiejian.DictationUiCheck
