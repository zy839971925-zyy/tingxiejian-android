#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
ROOT=$(pwd)
MODE=dev
COMPILE_ONLY=false
NO_MODELS=false
RELEASE_KS=${RELEASE_KEYSTORE:-}
RELEASE_STORE_PASS=${RELEASE_STORE_PASSWORD_FILE:-}
RELEASE_KEY_PASS=${RELEASE_KEY_PASSWORD_FILE:-}
RELEASE_ALIAS=${RELEASE_KEY_ALIAS:-}
RELEASE_PIN=${RELEASE_CERT_SHA256:-}
usage() {
  cat <<'USAGE'
Usage: bash build.sh [--compile-only | --dev-no-models] [--release signing inputs]
Default: a debuggable development APK with a separate development signing identity.
  --compile-only         Validate resources, Java and DEX only; produce no APK.
  --dev-no-models         Development validation APK without recognition models.
  --release              Non-debuggable release; external identity and provenance required.
  --release-keystore PATH --release-store-password-file PATH
  --release-key-password-file PATH --release-key-alias ALIAS
  --release-cert-sha256 HEX   Optional expected release certificate fingerprint.
Equivalent explicit RELEASE_* environment inputs are supported. Passwords are read from
private files; never put their values on the command line. All release signing files must
be outside the checkout. A development APK cannot update a differently signed installation.
USAGE
}
need_value() { test "$#" -ge 2 && test -n "$2" || { echo "Missing value for $1" >&2; exit 1; }; }
while test "$#" -gt 0; do
  case "$1" in
    --help|-h) usage; exit 0 ;;
    --release) MODE=release; shift ;;
    --compile-only) COMPILE_ONLY=true; shift ;;
    --dev-no-models) NO_MODELS=true; shift ;;
    --release-keystore) need_value "$@"; RELEASE_KS=$2; shift 2 ;;
    --release-store-password-file) need_value "$@"; RELEASE_STORE_PASS=$2; shift 2 ;;
    --release-key-password-file) need_value "$@"; RELEASE_KEY_PASS=$2; shift 2 ;;
    --release-key-alias) need_value "$@"; RELEASE_ALIAS=$2; shift 2 ;;
    --release-cert-sha256) need_value "$@"; RELEASE_PIN=$2; shift 2 ;;
    *) echo "Unknown build option: $1" >&2; usage >&2; exit 1 ;;
  esac
done
if test "$MODE" = release; then
  if "$COMPILE_ONLY" || "$NO_MODELS"; then
    echo 'Release cannot use --compile-only or --dev-no-models' >&2; exit 1
  fi
  RELEASE_CERT=$(python3 scripts/signing-policy.py --project-root "$ROOT" \
    --keystore "$RELEASE_KS" --store-password-file "$RELEASE_STORE_PASS" \
    --key-password-file "$RELEASE_KEY_PASS" --alias "$RELEASE_ALIAS" --cert-sha256 "$RELEASE_PIN")
  echo "Release certificate SHA-256: $RELEASE_CERT"
fi
if "$COMPILE_ONLY" && "$NO_MODELS"; then
  echo 'Choose --compile-only or --dev-no-models' >&2; exit 1
fi
# All compilation/packaging mutations share build/. A concurrent build must not erase
# another compiler's resources or change the aligned APK beneath a signer.
mkdir -p build
BUILD_LOCK=build/.build-lock
if ! mkdir "$BUILD_LOCK" 2>/dev/null; then
  echo 'Another build holds build/.build-lock. Check its pid; remove a stale lock only after that build exits.' >&2
  exit 1
fi
printf '%s\n' "$$" > "$BUILD_LOCK/pid"
release_build_lock() {
  rm -f "$BUILD_LOCK/pid"
  rmdir "$BUILD_LOCK" 2>/dev/null || true
}
trap release_build_lock EXIT
V=vendor
JAR="$V/android.jar"
AAR="$V/sherpa-onnx-1.13.8.aar"
KOTLIN="$V/kotlin-stdlib.jar"
HIDDEN="$V/hiddenapibypass.jar"
SHIZUKU_API="$V/shizuku-api.jar"
SHIZUKU_PROVIDER="$V/shizuku-provider.jar"
SHIZUKU_AIDL="$V/shizuku-aidl.jar"
SHIZUKU_SHARED="$V/shizuku-shared.jar"
for f in "$JAR" "$AAR" "$KOTLIN" "$HIDDEN" "$SHIZUKU_API" "$SHIZUKU_PROVIDER" "$SHIZUKU_AIDL" "$SHIZUKU_SHARED"; do
  test -s "$f" || { echo "Missing $f" >&2; exit 1; }
done
if ! javap -classpath "$JAR" 'android.app.Notification$ProgressStyle' >/dev/null 2>&1; then
  echo 'Compile SDK 36 android.jar is required for standard Live Update (minSdk 26 / targetSdk 35 unchanged).' >&2
  exit 1
fi
bash scripts/verify-libraries.sh
rm -rf build/obj build/gen build/dex build/lib build/app.jar build/res.zip build/base.apk build/unsigned.apk build/aligned.apk
mkdir -p build/obj build/gen build/dex build/lib/arm64-v8a dist
# Patch only the generated copy: IDs, Shizuku and Xiaomi metadata remain as supplied.
python3 - "$MODE" <<'PY'
from pathlib import Path
import re, sys
source = Path('AndroidManifest.xml').read_text()
if sys.argv[1] == 'dev':
    source, count = re.subn(r'android:debuggable="false"', 'android:debuggable="true"', source)
    if count != 1: raise SystemExit('expected exactly one debuggable=false application declaration')
    source = re.sub(r'android:versionName="([^"]+)"', lambda m: 'android:versionName="' + m[1] + '-dev"', source, count=1)
elif 'android:debuggable="false"' not in source:
    raise SystemExit('release source manifest must be non-debuggable')
Path('build/AndroidManifest.xml').write_text(source)
PY
aapt2 compile --dir res -o build/res.zip
aapt2 link --manifest build/AndroidManifest.xml -I "$JAR" -o build/base.apk \
  --java build/gen --auto-add-overlay --min-sdk-version 26 --target-sdk-version 35 -R build/res.zip
mapfile -t JAVA_SOURCES < <(find src build/gen -name '*.java' -print)
javac --release 8 -Xlint:-options -encoding UTF-8 \
  -cp "$JAR:$V/classes.jar:$KOTLIN:$HIDDEN:$SHIZUKU_API:$SHIZUKU_PROVIDER:$SHIZUKU_AIDL:$SHIZUKU_SHARED" \
  -d build/obj "${JAVA_SOURCES[@]}"
jar cf build/app.jar -C build/obj .
d8 --min-api 26 --lib "$JAR" --output build/dex \
  build/app.jar "$V/classes.jar" "$KOTLIN" "$HIDDEN" "$SHIZUKU_API" "$SHIZUKU_PROVIDER" "$SHIZUKU_AIDL" "$SHIZUKU_SHARED"
if "$COMPILE_ONLY"; then
  echo 'Compile-only verified: resources, Java and DEX. No installable APK or model readiness claimed.'
  exit 0
fi
for lib in libonnxruntime.so libsherpa-onnx-c-api.so libsherpa-onnx-cxx-api.so libsherpa-onnx-jni.so; do
  unzip -p "$AAR" "jni/arm64-v8a/$lib" > "build/lib/arm64-v8a/$lib"
done
cp build/base.apk build/unsigned.apk
PACKAGE_ARGS=()
if "$NO_MODELS"; then PACKAGE_ARGS+=(--no-models); fi
python3 scripts/package-apk.py --mode "$MODE" "${PACKAGE_ARGS[@]}"
zipalign -f -p 4 build/unsigned.apk build/aligned.apk
VERSION=$(python3 -c 'import xml.etree.ElementTree as E; print(E.parse("AndroidManifest.xml").getroot().get("{http://schemas.android.com/apk/res/android}versionName"))')
if test "$MODE" = release; then
  OUT="dist/tingxiejian-v${VERSION}-arm64-release.apk"
  bash scripts/sign-apk.sh "$RELEASE_KS" "$RELEASE_ALIAS" \
    "$RELEASE_STORE_PASS" "$RELEASE_KEY_PASS" "$OUT" build/aligned.apk
else
  DEV_DIR=build/dev-signing
  mkdir -p "$DEV_DIR"
  chmod 700 "$DEV_DIR"
  if test ! -f "$DEV_DIR/dev-keystore.p12"; then
    umask 077
    python3 -c 'import secrets; print(secrets.token_hex(32))' > "$DEV_DIR/dev.pass"
    keytool -genkeypair -noprompt -keystore "$DEV_DIR/dev-keystore.p12" -storetype PKCS12 \
      -storepass:file "$DEV_DIR/dev.pass" -keypass:file "$DEV_DIR/dev.pass" \
      -alias tingxiejian-dev -keyalg RSA -keysize 3072 -validity 3650 \
      -dname 'CN=Tingxiejian Development Only' >/dev/null
  fi
  test -s "$DEV_DIR/dev.pass" || { echo 'Development identity password missing; preserve or replace its paired files deliberately.' >&2; exit 1; }
  if "$NO_MODELS"; then SUFFIX=dev-no-models; else SUFFIX=dev; fi
  OUT="dist/tingxiejian-v${VERSION}-arm64-${SUFFIX}.apk"
  bash scripts/sign-apk.sh "$DEV_DIR/dev-keystore.p12" tingxiejian-dev \
    "$DEV_DIR/dev.pass" "$DEV_DIR/dev.pass" "$OUT" build/aligned.apk
fi
CERT_REPORT=$(apksigner verify --verbose --print-certs "$OUT")
printf '%s\n' "$CERT_REPORT"
if test "$MODE" = release; then
  SIGNED_CERT=$(printf '%s\n' "$CERT_REPORT" | sed -n 's/^Signer #1 certificate SHA-256 digest: //p')
  if test "$SIGNED_CERT" != "$RELEASE_CERT"; then
    rm -f "$OUT" "$OUT.idsig"
    echo 'Signed APK certificate differs from validated release identity' >&2; exit 1
  fi
fi
zipalign -c -p 4 "$OUT"
python3 scripts/package-apk.py --verify "$OUT" "${PACKAGE_ARGS[@]}"
python3 design-tools/check-apk-classes.py "$OUT"
ls -lh "$OUT"
