#!/usr/bin/env bash
# Obtain permissively licensed build dependencies from Maven Central. Models, MiSans, SDK and
# sherpa-onnx AAR are deliberately NOT fetched here: read docs/BUILDING.md and their licenses.
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p vendor
maven=https://repo.maven.apache.org/maven2
get() {
  local path="$1" dest="$2"
  local expected actual
  expected=$(awk -v file="vendor/$dest" '$2 == file {print $1}' scripts/maven-sha256.txt)
  test -n "$expected" || { echo "No approved SHA-256 for $dest" >&2; exit 1; }
  if test -s "vendor/$dest"; then
    actual=$(sha256sum "vendor/$dest" | cut -d' ' -f1)
    test "$actual" = "$expected" || { echo "Cached $dest has unexpected SHA-256; inspect/remove it" >&2; exit 1; }
    return
  fi
  echo "Fetching $dest"
  curl -fL --retry 3 --connect-timeout 15 "$maven/$path" -o "vendor/$dest.tmp"
  actual=$(sha256sum "vendor/$dest.tmp" | cut -d' ' -f1)
  test "$actual" = "$expected" || { rm -f "vendor/$dest.tmp"; echo "Downloaded $dest SHA-256 mismatch" >&2; exit 1; }
  mv "vendor/$dest.tmp" "vendor/$dest"
}
extract_classes() {
  local aar="$1" output="$2"
  if test -s "vendor/$output" && cmp -s <(unzip -p "vendor/$aar" classes.jar) "vendor/$output"; then return; fi
  unzip -p "vendor/$aar" classes.jar > "vendor/$output.tmp"
  test -s "vendor/$output.tmp"
  mv "vendor/$output.tmp" "vendor/$output"
}
get org/jetbrains/kotlin/kotlin-stdlib/1.7.20/kotlin-stdlib-1.7.20.jar kotlin-stdlib.jar
get org/json/json/20240303/json-20240303.jar json-desktop.jar
get org/lsposed/hiddenapibypass/hiddenapibypass/6.1/hiddenapibypass-6.1.aar hiddenapibypass-6.1.aar
extract_classes hiddenapibypass-6.1.aar hiddenapibypass.jar
for module in api provider aidl shared; do
  get "dev/rikka/shizuku/$module/13.1.5/$module-13.1.5.aar" "shizuku-$module-13.1.5.aar"
  extract_classes "shizuku-$module-13.1.5.aar" "shizuku-$module.jar"
done
if test -n "${ANDROID_HOME:-}" && test -s "$ANDROID_HOME/platforms/android-36/android.jar"; then
  cp "$ANDROID_HOME/platforms/android-36/android.jar" vendor/android.jar
fi
if test -s vendor/sherpa-onnx-1.13.8.aar; then
  extract_classes sherpa-onnx-1.13.8.aar classes.jar
fi
for f in vendor/android.jar vendor/sherpa-onnx-1.13.8.aar vendor/classes.jar; do
  if ! test -s "$f"; then
    echo "Manual prerequisite missing: $f (see docs/BUILDING.md)" >&2
    exit 1
  fi
done
bash scripts/verify-libraries.sh
echo "Libraries ready; model files are a separate licensed prerequisite."
