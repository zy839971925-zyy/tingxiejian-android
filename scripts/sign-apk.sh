#!/usr/bin/env bash
# Values stay in password files. Arguments: keystore alias store-pass-file key-pass-file output input.
set -euo pipefail
test "$#" -eq 6 || { echo 'sign-apk requires keystore, alias, password-file paths, output and input' >&2; exit 1; }
KS=$1
ALIAS=$2
STORE_FILE=$3
KEY_FILE=$4
OUT=$5
INPUT=$6
KEY_PASS_ARGS=()
# apksigner caches password-file streams. When both arguments name the same file,
# omit key-pass so it uses the already-read store password instead of reading EOF.
if ! test "$STORE_FILE" -ef "$KEY_FILE"; then KEY_PASS_ARGS=(--key-pass "file:$KEY_FILE"); fi
apksigner sign --min-sdk-version 26 --ks "$KS" --ks-key-alias "$ALIAS" \
  --ks-pass "file:$STORE_FILE" "${KEY_PASS_ARGS[@]}" --out "$OUT" "$INPUT"
