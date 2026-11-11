#!/usr/bin/env bash
# Gradi debug APK bez Gradle-a, samo alatima iz Android SDK-a, javac-a i kotlinc-a.
#
# Potrebno:
#   ANDROID_HOME   putanja do Android SDK-a (platforma android-35 i build-tools 35.0.0)
#   KOTLINC        putanja do kotlinc (podrazumijevano: kotlinc iz PATH-a)
#
# Redoslijed: resursi (XML, layout, meni) -> klasa R -> Kotlin -> DEX -> potpis.
# Rezultat: build/apk/mini-alexa-debug.apk
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
: "${ANDROID_HOME:?postavi ANDROID_HOME na putanju do Android SDK-a}"
KOTLINC="${KOTLINC:-kotlinc}"
BT="$ANDROID_HOME/build-tools/35.0.0"
PLATFORM_JAR="$ANDROID_HOME/platforms/android-35/android.jar"
KOTLINC_HOME="$(cd "$(dirname "$(readlink -f "$(command -v "$KOTLINC")")")/.." && pwd)"
KOTLIN_STDLIB="$KOTLINC_HOME/lib/kotlin-stdlib.jar"
RES="$ROOT/app/src/main/res"
MANIFEST="$ROOT/app/src/main/AndroidManifest.xml"
OUT="$ROOT/build/apk"
KEYSTORE="$ROOT/build/debug.keystore"

for f in "$BT/d8" "$BT/aapt2" "$BT/zipalign" "$BT/apksigner" "$PLATFORM_JAR" "$KOTLIN_STDLIB"; do
  [ -e "$f" ] || { echo "Nedostaje: $f"; exit 1; }
done
command -v javac >/dev/null || { echo "Nedostaje javac (JDK)"; exit 1; }

rm -rf "$OUT"
mkdir -p "$OUT/res" "$OUT/gen" "$OUT/rclasses" "$OUT/classes"

echo "1/6 Kompajliranje resursa"
"$BT/aapt2" compile --dir "$RES" -o "$OUT/res/compiled.zip"

echo "2/6 Povezivanje resursa i manifesta (generiše se klasa R)"
"$BT/aapt2" link -I "$PLATFORM_JAR" \
  --manifest "$MANIFEST" \
  --min-sdk-version 26 --target-sdk-version 35 \
  --java "$OUT/gen" \
  -o "$OUT/base.apk" \
  "$OUT/res/compiled.zip"

echo "3/6 Kompajliranje klase R (javac)"
javac -nowarn -d "$OUT/rclasses" -cp "$PLATFORM_JAR" $(find "$OUT/gen" -name '*.java') 2>&1 | grep -v -E "^Note:|JAVA_TOOL" || true
[ -n "$(find "$OUT/rclasses" -name 'R*.class' -print -quit)" ] || { echo "Klasa R nije napravljena"; exit 1; }

echo "4/6 Kompajliranje Kotlin koda (core + app)"
"$KOTLINC" "$ROOT/core/src/main/kotlin" "$ROOT/app/src/main/kotlin" \
  -cp "$PLATFORM_JAR:$OUT/rclasses" -d "$OUT/classes" 2>&1 | grep -E "^error:|^e:" || true
[ -n "$(find "$OUT/classes" -name '*.class' -print -quit)" ] || { echo "Kotlin kompajliranje nije uspjelo"; exit 1; }

echo "5/6 Pretvaranje u DEX i dodavanje u APK"
"$BT/d8" --min-api 26 --lib "$PLATFORM_JAR" --output "$OUT" \
  $(find "$OUT/classes" "$OUT/rclasses" -name '*.class') "$KOTLIN_STDLIB" >/dev/null 2>&1
python3 - "$OUT/base.apk" "$OUT/classes.dex" <<'PY'
import sys, zipfile
apk, dex = sys.argv[1], sys.argv[2]
with zipfile.ZipFile(apk, "a") as z:
    z.write(dex, "classes.dex")
PY
"$BT/zipalign" -f 4 "$OUT/base.apk" "$OUT/aligned.apk"

echo "6/6 Potpisivanje debug ključem"
mkdir -p "$ROOT/build"
if [ ! -f "$KEYSTORE" ]; then
  keytool -genkeypair -v -keystore "$KEYSTORE" -storepass android -keypass android \
    -alias debug -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Mini Alexa Debug" >/dev/null 2>&1
fi
"$BT/apksigner" sign --ks "$KEYSTORE" --ks-pass pass:android --key-pass pass:android \
  --out "$OUT/mini-alexa-debug.apk" "$OUT/aligned.apk"

echo "Gotovo: $OUT/mini-alexa-debug.apk"
