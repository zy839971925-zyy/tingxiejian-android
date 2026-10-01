#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
OUT=build/security-tests
mkdir -p "$OUT"
javac --release 8 -Xlint:-options -encoding UTF-8 -d "$OUT" src/com/example/tingxiejian/SecretMigration.java tests/security/SecretMigrationCheck.java
java -cp "$OUT" com.example.tingxiejian.SecretMigrationCheck
