#!/usr/bin/env bash
# Check pinned Maven bytes and that extracted compile jars match their parent AARs.
set -euo pipefail
cd "$(dirname "$0")/.."
sha256sum --check --status scripts/maven-sha256.txt || {
  echo 'Maven library missing/changed; rerun scripts/prepare-libraries.sh or inspect vendor/' >&2
  exit 1
}
for pair in 'hiddenapibypass-6.1.aar hiddenapibypass.jar' \
            'shizuku-api-13.1.5.aar shizuku-api.jar' \
            'shizuku-provider-13.1.5.aar shizuku-provider.jar' \
            'shizuku-aidl-13.1.5.aar shizuku-aidl.jar' \
            'shizuku-shared-13.1.5.aar shizuku-shared.jar' \
            'sherpa-onnx-1.13.8.aar classes.jar'; do
  read -r aar jar <<< "$pair"
  test -s "vendor/$aar" && test -s "vendor/$jar" && \
    cmp -s <(unzip -p "vendor/$aar" classes.jar) "vendor/$jar" || {
      echo "Jar does not match its AAR: vendor/$jar (source vendor/$aar)" >&2
      exit 1
    }
done
echo 'Pinned Maven libraries and extracted AAR classes verified'
