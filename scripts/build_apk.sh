#!/usr/bin/env bash
# Gradi debug APK bez Gradle-a, samo alatima iz Android SDK-a i kotlinc-a.
#
# Potrebno:
#   ANDROID_HOME   putanja do Android SDK-a (sa platformom android-35 i build-tools 35.0.0)
#   KOTLINC        putanja do kotlinc (podrazumijevano: kotlinc iz PATH-a)
#
# Rezultat: build/apk/mini-alexa-debug.apk
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
: "${ANDROID_HOME:?postavi ANDROID_HOME na putanju do Android SDK-a}"
KOTLINC="${KOTLINC:-kotlinc}"
BT="$ANDROID_HOME/build-tools/35.0.0"
PLATFORM_JAR="$ANDROID_HOME/platforms/android-35/android.jar"
KOTLINC_HOME="$(cd "$(dirname "$(readlink -f "$(command -v "$KOTLINC")")")/.." && pwd)"
KOTLIN_STDLIB="$KOTLINC_HOME/lib/kotlin-stdlib.jar"
OUT="$ROOT/build/apk"
KEYSTORE="$ROOT/build/debug.keystore"

for f in "$BT/d8" "$BT/aapt2" "$BT/zipalign" "$BT/apksigner" "$PLATFORM_JAR" "$KOTLIN_STDLIB"; do
  [ -e "$f" ] || { echo "Nedostaje: $f"; exit 1; }
done

rm -rf "$OUT"
mkdir -p "$OUT/classes"

echo "1/5 Kompajliranje (core + app)"
"$KOTLINC" "$ROOT/core/src/main/kotlin" "$ROOT/app/src/main/kotlin" \
  -cp "$PLATFORM_JAR" -d "$OUT/classes" 2>&1 | grep -v "^warning" || true
[ -n "$(find "$OUT/classes" -name '*.class' -print -quit)" ] || { echo "Kompajliranje nije uspjelo"; exit 1; }

echo "2/5 Pretvaranje u DEX"
"$BT/d8" --min-api 26 --lib "$PLATFORM_JAR" --output "$OUT" \
  $(find "$OUT/classes" -name '*.class') "$KOTLIN_STDLIB"

echo "3/5 Povezivanje manifesta"
"$BT/aapt2" link -I "$PLATFORM_JAR" \
  --manifest "$ROOT/app/src/main/AndroidManifest.xml" \
  --min-sdk-version 26 --target-sdk-version 35 \
  -o "$OUT/base.apk"

echo "4/5 Dodavanje classes.dex i poravnanje"
python3 - "$OUT/base.apk" "$OUT/classes.dex" <<'PY'
import sys, zipfile
apk, dex = sys.argv[1], sys.argv[2]
with zipfile.ZipFile(apk, "a") as z:
    z.write(dex, "classes.dex")
PY
"$BT/zipalign" -f 4 "$OUT/base.apk" "$OUT/aligned.apk"

echo "5/5 Potpisivanje debug ključem"
mkdir -p "$ROOT/build"
if [ ! -f "$KEYSTORE" ]; then
  keytool -genkeypair -v -keystore "$KEYSTORE" -storepass android -keypass android \
    -alias debug -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Mini Alexa Debug" >/dev/null 2>&1
fi
"$BT/apksigner" sign --ks "$KEYSTORE" --ks-pass pass:android --key-pass pass:android \
  --out "$OUT/mini-alexa-debug.apk" "$OUT/aligned.apk"

echo "Gotovo: $OUT/mini-alexa-debug.apk"
