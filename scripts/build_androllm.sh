#!/usr/bin/env bash
# Gradi AndroLLM (debug APK) iz jednog foldera sa izvornim kodom.
#
# Upotreba:
#   ANDROID_HOME=/putanja/do/sdk ./scripts/build_androllm.sh /putanja/do/AndroLLM
#
# Potrebno: Android SDK 36, NDK 26.1.10909125 i CMake 3.22.1 u ANDROID_HOME, i JDK 17+.
set -euo pipefail

SRC="${1:?navedi folder sa AndroLLM kodom}"
: "${ANDROID_HOME:?postavi ANDROID_HOME}"
LOG="${LOG:-$SRC/build.log}"

cd "$SRC"
echo "sdk.dir=$ANDROID_HOME" > local.properties
chmod +x gradlew
./gradlew :app:assembleDebug --no-daemon --console=plain 2>&1 | tee "$LOG"
echo "Gotovo. APK: $(find app/build/outputs/apk -name '*.apk' | head -1)"
