#!/bin/bash
# 预取 Robolectric 运行 Android 单元测试所需的 android-all 镜像 jar（144MB，一次性）。
# 用法：bash tools/fetch_robolectric.sh [http://代理地址:端口]
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEST="$ROOT/build/robolectric-deps"
VER="14-robolectric-10818077-i6"          # Robolectric 4.13 / SDK 34
JAR="android-all-instrumented-$VER.jar"
URL="https://repo1.maven.org/maven2/org/robolectric/android-all-instrumented/$VER/$JAR"
PROXY="${1:-}"
mkdir -p "$DEST"
if [ -s "$DEST/$JAR" ]; then echo "已存在：$DEST/$JAR"; exit 0; fi
CURL=(curl -fL --retry 3 --max-time 900 -o "$DEST/$JAR" "$URL")
[ -n "$PROXY" ] && CURL=(-x "$PROXY" "${CURL[@]}")
echo "下载 $URL"
"${CURL[@]}"
ls -lh "$DEST/$JAR"
