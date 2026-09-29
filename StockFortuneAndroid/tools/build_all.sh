#!/bin/bash
# 股运通 一键构建流水线（完全本地、可复现）。
# 顺序不能调整：Room 的 schema JSON 必须先于预置库生成，预置库的 DDL 直接取自该 JSON。
set -euo pipefail
cd "$(dirname "$0")/.."

export JAVA_HOME="${JAVA_HOME:-$HOME/tools/jdk-17/Contents/Home}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
GRADLE="${GRADLE:-$HOME/tools/gradle-8.11.1/bin/gradle}"
PY=python3
export PATH="$JAVA_HOME/bin:$PATH"

# 签名口令只存在于用户目录，不随交付树分发
if [ -f "$HOME/.stockfortune-keystore/secrets.env" ]; then
  set -a
  # shellcheck disable=SC1091
  . "$HOME/.stockfortune-keystore/secrets.env"
  set +a
fi

echo "[1/7] 生成/校验干支与交易日历、导入 Excel → 预置库"
(cd tools && $PY build_database.py --start 1990-12-01 --end 2035-12-31 >/dev/null)

echo "[2/7] 编译（产出 app/schemas/**/1.json 与 Room identityHash）"
"$GRADLE" --console=plain -q compileDebugKotlin

echo "[3/7] 用 Room 导出的 DDL 重建预置库 + 注入 identityHash"
(cd tools && $PY build_database.py --start 1990-12-01 --end 2035-12-31 >/dev/null && $PY inject_room_hash.py | tail -1)

echo "[4/7] 数据层门禁（31 项）"
(cd tools && $PY verify_database.py | tail -1)

echo "[5/7] 生成 parity 夹具并跑 JVM 单元测试 + Robolectric 运行时冒烟"
(cd tools && $PY gen_parity_fixtures.py >/dev/null)
"$GRADLE" --console=plain -q testDebugUnitTest

echo "[6/7] lint（Error 级即失败）+ 打包 debug/release APK"
"$GRADLE" --console=plain -q lintRelease
"$GRADLE" --console=plain -q assembleDebug assembleRelease
mkdir -p release
cp app/build/outputs/apk/debug/app-debug.apk release/stock-fortune-debug.apk
cp app/build/outputs/apk/release/app-release.apk release/stock-fortune-release.apk

echo "[7/7] 签名/对齐/校验和证据归档到 release/verification/"
APKSIGNER="$(ls "$ANDROID_HOME"/build-tools/*/apksigner | tail -1)"
ZIPALIGN="$(ls "$ANDROID_HOME"/build-tools/*/zipalign | tail -1)"
AAPT2="$(ls "$ANDROID_HOME"/build-tools/*/aapt2 | tail -1)"
EV=release/verification/BUILD_EVIDENCE.txt
{
  echo "# 构建证据 $(date '+%Y-%m-%d %H:%M:%S')"
  echo "## apksigner verify --verbose --print-certs (release)"
  "$APKSIGNER" verify --verbose --print-certs release/stock-fortune-release.apk
  echo "## zipalign -c 4 (release)"
  "$ZIPALIGN" -c 4 release/stock-fortune-release.apk && echo "zipalign OK"
  echo "## sha256"
  shasum -a 256 release/stock-fortune-release.apk release/stock-fortune-debug.apk
  echo "## aapt2 dump permissions (release)"
  "$AAPT2" dump permissions release/stock-fortune-release.apk
  echo "## aapt2 dump badging 关键行 (release)"
  "$AAPT2" dump badging release/stock-fortune-release.apk | grep -E "package:|sdkVersion|targetSdk|uses-permission|application-label" || true
} > "$EV" 2>&1
"$APKSIGNER" verify release/stock-fortune-release.apk && echo "release 签名校验通过"
ls -lh release/*.apk
echo "BUILD_ALL_OK"
