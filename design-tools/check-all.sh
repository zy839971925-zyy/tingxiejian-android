#!/usr/bin/env bash
# Every check that can run without a phone in someone's hand.
set -uo pipefail
cd "$(dirname "$0")/.."
status=0
run() { echo "--- $*"; "$@" || status=1; }
run bash design-tools/native-logic-check.sh
run python design-tools/check-wiring.py
run python design-tools/check-visual.py
run python design-tools/check-portal.py
run python design-tools/check-first-run.py
run python design-tools/check-cloud.py
run python design-tools/check-island-gate.py
run bash design-tools/island-payload-check.sh
echo
[ $status -eq 0 ] && echo "ALL CHECKS PASSED" || echo "SOMETHING FAILED"
exit $status
