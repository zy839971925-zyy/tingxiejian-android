#!/usr/bin/env bash
# Every check that can run without a phone in someone's hand.
set -uo pipefail
cd "$(dirname "$0")/.."
status=0
run() { echo "--- $*"; "$@" || status=1; }
run bash design-tools/native-logic-check.sh
run bash design-tools/export-check.sh
run python design-tools/check-wiring.py
run python design-tools/check-visual.py
run python design-tools/check-portal.py
run python design-tools/check-first-run.py
run python3 design-tools/check-feedback.py
run python design-tools/check-cloud.py
run python design-tools/check-island-gate.py
run python design-tools/check-audit-guards.py
run python design-tools/check-docs.py
run bash design-tools/island-payload-check.sh
run python3 design-tools/island-characterization.py
run bash design-tools/pipeline-logic-check.sh
run bash design-tools/microphone-check.sh
run bash design-tools/dictation-ui-check.sh
run bash design-tools/ui-motion-check.sh
run bash design-tools/live-update-check.sh
run python3 design-tools/check-live-update.py
run bash design-tools/model-logic-check.sh
run bash design-tools/session-check.sh
run bash design-tools/polish-check.sh
run bash tests/security/secret-check.sh
run bash tests/security/cloud-check.sh
run python3 tests/security/signing_check.py
run python3 tests/security/build_cli_check.py
run python3 tests/security/package_check.py
run python3 -m unittest discover -s design-tools -p test_model_manifest.py
run python3 -m unittest discover -s benchmark -p 'test_*.py'
run python3 scripts/validate-model-provenance.py
if command -v aapt2 >/dev/null && command -v apksigner >/dev/null && test -s vendor/android.jar; then
  run python3 tests/security/apk_signing_check.py
else
  echo 'SKIP: actual APK signing fixture requires Android SDK; pure release policy still tested above'
fi
echo
[ $status -eq 0 ] && echo "ALL CHECKS PASSED" || echo "SOMETHING FAILED"
exit $status
