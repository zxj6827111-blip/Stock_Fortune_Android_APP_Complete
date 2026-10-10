#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

echo "=== [1/4] Checking Environment ==="
if [ -z "${JAVA_HOME:-}" ]; then
  if [ -d "/Users/zhangxingjin/tools/jdk-17/Contents/Home" ]; then
    export JAVA_HOME="/Users/zhangxingjin/tools/jdk-17/Contents/Home"
    export PATH="$JAVA_HOME/bin:$PATH"
  fi
fi
echo "JAVA_HOME: ${JAVA_HOME:-unknown}"
java -version

echo "=== [2/4] Running 127 Unit & Parity Tests in Android Runtime ==="
cd "${PROJECT_ROOT}"
./gradlew testDebugUnitTest --offline

echo "=== [3/4] Assembling Debug APK ==="
./gradlew assembleDebug --offline

echo "=== [4/4] Verifying Artifact Sizes and Checksums ==="
DB_PATH="${PROJECT_ROOT}/app/src/main/assets/databases/stock_fortune.db"
RULES_PATH="${PROJECT_ROOT}/app/src/main/assets/copywriting/copy_rules_frozen_281.json"
APK_PATH="${PROJECT_ROOT}/app/build/outputs/apk/debug/app-debug.apk"

echo "DB Size: $(stat -f "%z" "$DB_PATH" 2>/dev/null || stat -c "%s" "$DB_PATH") bytes"
echo "Rules Size: $(stat -f "%z" "$RULES_PATH" 2>/dev/null || stat -c "%s" "$RULES_PATH") bytes"
echo "APK Size: $(stat -f "%z" "$APK_PATH" 2>/dev/null || stat -c "%s" "$APK_PATH") bytes"

shasum -a 256 "$DB_PATH" "$RULES_PATH" "$APK_PATH" 2>/dev/null || sha256sum "$DB_PATH" "$RULES_PATH" "$APK_PATH"

echo "=== Phase 7 All Acceptance Checks Completed Successfully ==="
