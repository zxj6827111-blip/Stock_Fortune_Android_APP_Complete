#!/bin/bash
# 由 design_assets/logo/app_icon.png 生成启动图标各密度资源。
# 原图为 1254x1254 的"八卦徽标 + 股运通文字"组合图，图标只取圆形徽标部分。
set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="$ROOT/../Stock_Fortune_Android_APP_AI_Start_Kit/design_assets/logo/app_icon.png"
OUT="$ROOT/app/src/main/res"
TMP="$(mktemp -d)"
# 徽标区：上 18px 起、边长 762（目视校准，避开下方文字）
sips -s format png --cropToHeightWidth 762 762 --cropOffset 18 246 "$SRC" --out "$TMP/emblem.png" >/dev/null
for d in mdpi:48 hdpi:72 xhdpi:96 xxhdpi:144 xxxhdpi:192; do
  dir="${d%%:*}"; size="${d##*:}"
  mkdir -p "$OUT/mipmap-$dir"
  sips -z "$size" "$size" "$TMP/emblem.png" --out "$OUT/mipmap-$dir/ic_launcher.png" >/dev/null
  cp "$OUT/mipmap-$dir/ic_launcher.png" "$OUT/mipmap-$dir/ic_launcher_round.png"
done
# 启动页 Logo（保留完整含字图，缩到 1080 宽以内）
mkdir -p "$OUT/drawable-nodpi"
sips -Z 1080 "$SRC" --out "$OUT/drawable-nodpi/brand_logo.png" >/dev/null
rm -rf "$TMP"
echo "icons generated"
