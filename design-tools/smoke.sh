#!/usr/bin/env bash
# Launches the installed app from Termux and reads back what the app wrote about itself.
# This is the loop for "it crashes on launch" without needing a person to tap anything.
set -uo pipefail
cd "$(dirname "$0")/.."
PKG=com.example.tingxiejian
DL=/sdcard/Download
FILES="tingxiejian-boot.txt tingxiejian-last-crash.txt tingxiejian-problem.txt tingxiejian-layout.json"

echo "== clearing previous evidence =="
for f in $FILES; do rm -f "$DL/$f"; done

echo "== launching $PKG =="
am start -n "$PKG/.MainActivity" 2>&1 | tail -2
sleep 4

echo "== what the app reported =="
for f in $FILES; do
  if [ -f "$DL/$f" ]; then
    echo "----- $f"
    sed -n '1,80p' "$DL/$f"
  else
    echo "----- $f  ** MISSING **"
  fi
done

echo "== verdict =="
if [ -f "$DL/tingxiejian-last-crash.txt" ]; then
  echo "CRASHED: see tingxiejian-last-crash.txt above"
elif [ -f "$DL/tingxiejian-layout.json" ]; then
  echo "OPENED: first frame measured"
  python design-tools/check-layout.py
else
  echo "NO EVIDENCE: the process died before writing anything (native crash, or killed before onCreate)"
fi
