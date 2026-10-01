#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
OUT=build/model-logic-check
mkdir -p "$OUT"
javac -encoding UTF-8 -cp vendor/json-desktop.jar -d "$OUT" \
  design-tools/models/stubs/android/content/Context.java \
  design-tools/models/stubs/android/content/res/AssetFileDescriptor.java \
  design-tools/models/stubs/android/content/res/AssetManager.java \
  src/com/example/tingxiejian/ModelInstaller.java \
  src/com/example/tingxiejian/ModelManager.java \
  src/com/example/tingxiejian/ModelPrep.java \
  design-tools/models/ModelInstallerCheck.java \
  design-tools/models/ModelManagerCheck.java
java -cp "$OUT:vendor/json-desktop.jar" com.example.tingxiejian.ModelInstallerCheck
java -cp "$OUT:vendor/json-desktop.jar" com.example.tingxiejian.ModelManagerCheck
