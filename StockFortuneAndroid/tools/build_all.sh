#!/bin/bash
# 股运通 一键构建流水线（完全本地、可复现）。
# 顺序不能调整：Room 的 schema JSON 必须先于预置库生成，预置库的 DDL 直接取自该 JSON。
set -euo pipefail
cd "$(dirname "$0")/.."

export JAVA_HOME="${JAVA_HOME:-$HOME/tools/jdk-17/Contents/Home}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
# 优先用仓库自带的 wrapper：旧实现默认 $HOME/tools/gradle-8.11.1/bin/gradle，
# 换一台机器（含 CI）就必须在固定路径手工装 Gradle。
GRADLE="${GRADLE:-./gradlew}"
# 构建器依赖 lunar-python；本机默认 python3 未必装了它，必须可覆盖并先行断言。
PY="${SF_PY:-python3}"
export PATH="$JAVA_HOME/bin:$PATH"

# --- 前置断言：缺什么直接说缺什么，而不是在第 [1/9] 步抛 traceback
if [ ! -f "$JAVA_HOME/bin/java" ] && ! command -v java >/dev/null 2>&1; then
  echo "缺少 JDK：设 JAVA_HOME 或安装 JDK 17+" >&2; exit 1
fi
if ! command -v "${PY%% *}" >/dev/null 2>&1; then
  echo "缺少 Python：$PY 不可用，可用 SF_PY=/usr/bin/python3 bash tools/build_all.sh 覆盖" >&2; exit 1
fi
if ! "$PY" -c 'import lunar_python' >/dev/null 2>&1; then
  echo "缺少依赖 lunar_python：先执行 $PY -m pip install -r tools/requirements.txt" >&2; exit 1
fi
XLSX="${SF_INPUT_XLSX:-../Stock_Fortune_Android_APP_AI_Start_Kit/input_data/生辰八字.xlsx}"
if [ ! -f "$XLSX" ]; then
  echo "缺少数据源 $XLSX（该目录不入库）。把它放到上述路径，或用 SF_INPUT_XLSX=<路径> 指定并同步给 build_database.py --xlsx。" >&2
  exit 1
fi

# 签名口令只存在于用户目录，不随交付树分发
if [ -f "$HOME/.stockfortune-keystore/secrets.env" ]; then
  set -a
  # shellcheck disable=SC1091
  . "$HOME/.stockfortune-keystore/secrets.env"
  set +a
fi

# 门禁输出：成功只留摘要行，失败时把完整明细打出来。
# 旧实现一律 `| tail -1`，而 verify_* 的失败是逐行 FAIL 明细 —— 等于自己销毁排障现场。
run_gate() {
  local out rc=0
  out="$("$@" 2>&1)" || rc=$?
  if [ "$rc" -ne 0 ]; then printf '%s\n' "$out"; return "$rc"; fi
  printf '%s\n' "$out" | tail -1
}

echo "[1/9] 生成/校验干支与交易日历、导入 Excel → 预置库"
(cd tools && "$PY" build_database.py --start 1990-12-01 --end 2035-12-31 >/dev/null)

echo "[2/9] 编译（产出 app/schemas/**/1.json 与 Room identityHash）"
"$GRADLE" --console=plain -q compileDebugKotlin

echo "[3/9] 用 Room 导出的 DDL 重建预置库 + 注入 identityHash"
(cd tools && "$PY" build_database.py --start 1990-12-01 --end 2035-12-31 >/dev/null \
  && "$PY" inject_room_hash.py | tail -1)

echo "[4/9] 数据层门禁"
(cd tools && run_gate "$PY" verify_database.py)

echo "[5/9] 古籍引文语料门禁"
(cd tools && run_gate "$PY" verify_classics.py)

echo "[6/9] 构建器行为测试（抽取 / 语料门禁 / 交易日历）"
(cd tools && "$PY" -m unittest discover -s tests -p 'test_*.py' 2>&1 | tail -3)

echo "[7/9] 生成 parity 夹具并跑 JVM 单元测试 + Robolectric 运行时冒烟"
(cd tools && "$PY" gen_parity_fixtures.py >/dev/null)
"$GRADLE" --console=plain -q testDebugUnitTest

echo "[8/9] lint（Error 级即失败）+ 打包 debug/release APK"
"$GRADLE" --console=plain -q lintRelease
"$GRADLE" --console=plain -q assembleDebug assembleRelease
mkdir -p release
cp app/build/outputs/apk/debug/app-debug.apk release/stock-fortune-debug.apk
cp app/build/outputs/apk/release/app-release.apk release/stock-fortune-release.apk

# 资产在 APK 里才算交付：本次 classics 特性就出现过"源码有、包里无"的归档包。
for want in assets/databases/stock_fortune.db assets/classics/ditiansui_jiyao.json; do
  if ! unzip -l release/stock-fortune-release.apk "$want" >/dev/null 2>&1; then
    echo "release APK 内缺少 $want —— 该包不可交付" >&2; exit 1
  fi
done

echo "[9/9] 签名/对齐/校验和证据归档到 release/verification/"
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
  echo "## APK 资产清单"
  unzip -l release/stock-fortune-release.apk | grep "assets/" || true
  echo "## aapt2 dump permissions (release)"
  "$AAPT2" dump permissions release/stock-fortune-release.apk
  echo "## aapt2 dump badging 关键行 (release)"
  "$AAPT2" dump badging release/stock-fortune-release.apk | grep -E "package:|sdkVersion|targetSdk|uses-permission|application-label" || true
} > "$EV" 2>&1
"$APKSIGNER" verify release/stock-fortune-release.apk && echo "release 签名校验通过"
# R8 开启后崩溃栈是混淆的，没有 mapping.txt 就反解不出来；旧实现不归档它。
if [ -f app/build/outputs/mapping/release/mapping.txt ]; then
  cp app/build/outputs/mapping/release/mapping.txt release/verification/mapping-release.txt
  echo "mapping.txt 已归档 release/verification/mapping-release.txt"
fi
ls -lh release/*.apk
echo "BUILD_ALL_OK"
