#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
ROOT=$(pwd)
V=vendor
JAR="$V/android.jar"
AAR="$V/sherpa-onnx-1.13.8.aar"
KOTLIN="$V/kotlin-stdlib.jar"
HIDDEN="$V/hiddenapibypass.jar"
SHIZUKU_API="$V/shizuku-api.jar"
SHIZUKU_PROVIDER="$V/shizuku-provider.jar"
SHIZUKU_AIDL="$V/shizuku-aidl.jar"
SHIZUKU_SHARED="$V/shizuku-shared.jar"
for f in "$JAR" "$AAR" "$KOTLIN" "$HIDDEN" "$SHIZUKU_API" "$SHIZUKU_PROVIDER" "$SHIZUKU_AIDL" "$SHIZUKU_SHARED"; do test -s "$f" || { echo "Missing $f" >&2;exit 1; };done
rm -rf build/obj build/gen build/dex build/lib build/app.jar build/res.zip build/base.apk build/unsigned.apk build/aligned.apk
mkdir -p build/obj build/gen build/dex build/lib/arm64-v8a dist

aapt2 compile --dir res -o build/res.zip
aapt2 link --manifest AndroidManifest.xml -I "$JAR" -o build/base.apk \
  --java build/gen --auto-add-overlay --min-sdk-version 26 --target-sdk-version 35 -R build/res.zip
javac --release 8 -encoding UTF-8 -cp "$JAR:$V/classes.jar:$KOTLIN:$HIDDEN:$SHIZUKU_API:$SHIZUKU_PROVIDER:$SHIZUKU_AIDL:$SHIZUKU_SHARED" \
  -d build/obj $(find src build/gen -name '*.java')
jar cf build/app.jar -C build/obj .
d8 --min-api 26 --lib "$JAR" --output build/dex \
  build/app.jar "$V/classes.jar" "$KOTLIN" "$HIDDEN" "$SHIZUKU_API" "$SHIZUKU_PROVIDER" "$SHIZUKU_AIDL" "$SHIZUKU_SHARED"
for lib in libonnxruntime.so libsherpa-onnx-c-api.so libsherpa-onnx-cxx-api.so libsherpa-onnx-jni.so; do
  unzip -p "$AAR" "jni/arm64-v8a/$lib" > "build/lib/arm64-v8a/$lib"
done
cp build/base.apk build/unsigned.apk
python - "$ROOT" <<'PY'
from pathlib import Path
import sys,zipfile
root=Path(sys.argv[1])
base=root
stream=root/'models/sherpa-onnx-streaming-zipformer-zh-int8-2025-06-30'
files={
 'stream-encoder.onnx':stream/'encoder.int8.onnx',
 'stream-decoder.onnx':stream/'decoder.onnx',
 'stream-joiner.onnx':stream/'joiner.int8.onnx',
 'stream-tokens.txt':stream/'tokens.txt',
 'offline-paraformer.onnx':base/'models/sherpa-onnx-paraformer-zh-2023-09-14/model.int8.onnx',
 'offline-tokens.txt':base/'models/sherpa-onnx-paraformer-zh-2023-09-14/tokens.txt',
 'punct.onnx':base/'models/sherpa-onnx-punct-ct-transformer-zh-en-vocab272727-2024-04-12-int8/model.int8.onnx',
 'diar-segmentation.onnx':base/'models/sherpa-onnx-pyannote-segmentation-3-0/model.int8.onnx',
 'diar-embedding.onnx':base/'models/embed.onnx',
}
with zipfile.ZipFile(root/'build/unsigned.apk','a',allowZip64=True) as apk:
    apk.write(root/'build/dex/classes.dex','classes.dex',compress_type=zipfile.ZIP_DEFLATED)
    apk.write(root/'THIRD_PARTY_NOTICES.md','assets/THIRD_PARTY_NOTICES.md',compress_type=zipfile.ZIP_DEFLATED)
    for lib in (root/'build/lib/arm64-v8a').glob('*.so'):
        apk.write(lib,'lib/arm64-v8a/'+lib.name,compress_type=zipfile.ZIP_DEFLATED)
    for name,path in files.items():
        if not path.is_file():raise SystemExit('Missing model: '+str(path))
        print('Bundling',name,round(path.stat().st_size/1048576,1),'MB',flush=True)
        # APK assets must be stored (not deflated) so AssetManager.openFd can read length.
        apk.write(path,'assets/model/'+name,compress_type=zipfile.ZIP_STORED)
PY
zipalign -f -p 4 build/unsigned.apk build/aligned.apk
if test ! -f build/debug-keystore.p12; then
  openssl rand -hex 24 > build/debug-keystore.pass
  chmod 600 build/debug-keystore.pass
  keytool -genkeypair -noprompt -keystore build/debug-keystore.p12 -storetype PKCS12 \
    -storepass "$(cat build/debug-keystore.pass)" -keypass "$(cat build/debug-keystore.pass)" \
    -alias tingxiejian -keyalg RSA -keysize 3072 -validity 3650 \
    -dname 'CN=Tingxiejian Local Build' >/dev/null
fi
cp build/debug-keystore.pass build/debug-key.pass
chmod 600 build/debug-key.pass
# Retain the v0.17 signing identity so V1.0 can upgrade without uninstalling or losing data.
OUT=dist/tingxiejian-v1.0-arm64-release.apk
apksigner sign --ks build/debug-keystore.p12 --ks-key-alias tingxiejian \
  --ks-pass file:build/debug-keystore.pass --key-pass file:build/debug-key.pass \
  --out "$OUT" build/aligned.apk
apksigner verify --verbose --print-certs "$OUT" | head -24
zipalign -c -p 4 "$OUT"
python - "$OUT" <<'PY'
from zipfile import ZipFile, ZIP_STORED
import sys
# Guard the two packaging invariants the app depends on at runtime.
with ZipFile(sys.argv[1]) as apk:
    bad = apk.testzip()
    if bad:
        raise SystemExit('corrupt zip entry: ' + bad)
    models = [i for i in apk.infolist() if i.filename.startswith('assets/model/')]
    if len(models) != 9:
        raise SystemExit(f'expected 9 bundled models, found {len(models)}')
    loose = [i.filename for i in models if i.compress_type != ZIP_STORED]
    if loose:
        raise SystemExit('AssetFileDescriptor needs STORED assets: ' + ', '.join(loose))
    for name in ('classes.dex', 'resources.arsc', 'res/layout/activity_main.xml',
                 'assets/THIRD_PARTY_NOTICES.md'):
        if name not in apk.namelist():
            raise SystemExit('missing ' + name)
    if not any(n.startswith('lib/arm64-v8a/') for n in apk.namelist()):
        raise SystemExit('missing native libraries')
print('packaging verified: 9 stored models, dex, native UI resources and native libs present')
PY
python3 design-tools/check-apk-classes.py "$OUT"
ls -lh "$OUT"
