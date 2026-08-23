#!/usr/bin/env bash
# ============================================================================
# Builds, aligns, and signs the APK without Gradle — the pipeline is plain
# aapt2 -> javac -> d8 -> zipalign -> apksigner, so every step is inspectable.
#
# Output: dist/KimiAccessWidget-debug.apk   (signed with a local debug key)
# ============================================================================
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TC="${KIMI_TALK_TOOLCHAIN:-/tmp/toolchain}"
SDK="$TC/sdk"
BT="$SDK/build-tools/35.0.0"
ANDROID_JAR="$SDK/platforms/android-35/android.jar"
KEYSTORE="$ROOT/keystore/debug.keystore"
# Build intermediates live under the repo by default; point
# KIMI_TALK_BUILD_DIR elsewhere (e.g. /tmp) on filesystems that are slow or
# unreliable for many small writes.
BUILD="${KIMI_TALK_BUILD_DIR:-$ROOT/build/apk}"
DIST="$ROOT/dist"

for f in "$ANDROID_JAR" "$BT/aapt2" "$BT/d8" "$BT/zipalign" "$BT/apksigner"; do
    [ -e "$f" ] || { echo "Missing $f — run env/setup.sh first."; exit 1; }
done

echo "==> [0/6] Icons"
# Launcher PNG fallbacks are generated, not committed; the widget drawables
# (ic_k, ic_more, previews) are committed vectors and must stay that way —
# gen_icons.py only writes mipmap-*/ic_launcher.png and removes stale PNGs.
if [ ! -f "$ROOT/app/src/main/res/mipmap-mdpi/ic_launcher.png" ]; then
    python3 "$ROOT/tools/gen_icons.py"
fi

rm -rf "$BUILD"
mkdir -p "$BUILD/gen" "$BUILD/classes" "$BUILD/dex"
mkdir -p "$DIST"

echo "==> [1/6] aapt2 compile resources"
"$BT/aapt2" compile --dir "$ROOT/app/src/main/res" -o "$BUILD/compiled.zip"

echo "==> [2/6] aapt2 link (resources.arsc + R.java + base APK)"
"$BT/aapt2" link \
    -o "$BUILD/app-unaligned.apk" \
    -I "$ANDROID_JAR" \
    --manifest "$ROOT/app/src/main/AndroidManifest.xml" \
    --java "$BUILD/gen" \
    --min-sdk-version 23 \
    --target-sdk-version 35 \
    "$BUILD/compiled.zip"

echo "==> [3/6] javac"
find "$ROOT/app/src/main/java" "$BUILD/gen" -name '*.java' > "$BUILD/sources.txt"
javac -encoding UTF-8 --release 8 \
    -classpath "$ANDROID_JAR" \
    -d "$BUILD/classes" \
    @"$BUILD/sources.txt"

echo "==> [4/6] d8 (dex)"
# Pass class files as direct arguments: d8's @argfile reader can choke on
# some filesystems (network/FUSE mounts), and the class list is short.
CLASSES=$(find "$BUILD/classes" -name '*.class')
"$BT/d8" --release --min-api 23 --lib "$ANDROID_JAR" \
    --output "$BUILD/dex" $CLASSES
( cd "$BUILD/dex" && zip -q -j "$BUILD/app-unaligned.apk" classes.dex )

echo "==> [5/6] zipalign + sign"
if [ ! -f "$KEYSTORE" ]; then
    mkdir -p "$ROOT/keystore"
    keytool -genkeypair -v -keystore "$KEYSTORE" -alias kimitalk \
        -keyalg RSA -keysize 2048 -validity 10000 \
        -storepass android -keypass android \
        -dname "CN=Kimi Talk Widget Debug, O=Personal, C=US" >/dev/null
    echo "    generated debug keystore at keystore/debug.keystore"
fi
"$BT/zipalign" -f -p 4 "$BUILD/app-unaligned.apk" "$BUILD/app-aligned.apk"
"$BT/apksigner" sign \
    --ks "$KEYSTORE" --ks-pass pass:android --key-pass pass:android \
    --out "$DIST/KimiAccessWidget-debug.apk" \
    "$BUILD/app-aligned.apk"

echo "==> [6/6] verify"
"$BT/apksigner" verify --print-certs "$DIST/KimiAccessWidget-debug.apk" | head -2
"$BT/aapt2" dump badging "$DIST/KimiAccessWidget-debug.apk" | grep -E "^package|^application-label" || true

echo ""
echo "APK ready: $DIST/KimiAccessWidget-debug.apk"
echo "Install with:  adb install -r $DIST/KimiAccessWidget-debug.apk"
