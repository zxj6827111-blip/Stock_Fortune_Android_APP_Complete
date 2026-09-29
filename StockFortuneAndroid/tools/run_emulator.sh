#!/bin/bash
# 启动本机 Android 模拟器（sf_avd / Android 15 arm64），供本地验收测试使用。
set -euo pipefail
export JAVA_HOME="${JAVA_HOME:-$HOME/tools/jdk-17/Contents/Home}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export ANDROID_AVD_HOME="$HOME/Library/Android/avd"
if [ ! -d "$ANDROID_AVD_HOME/sf_avd.avd" ]; then
  echo "AVD 不存在，正在创建..."
  mkdir -p "$ANDROID_AVD_HOME"
  $ANDROID_HOME/cmdline-tools/latest/bin/avdmanager create avd -n sf_avd \
    -k "system-images;android-35;google_apis;arm64-v8a" -d pixel_7 --force < /dev/null
fi
exec $ANDROID_HOME/emulator/emulator -avd sf_avd -gpu host -no-boot-anim -no-snapshot-save "$@"
