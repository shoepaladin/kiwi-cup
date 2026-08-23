#!/usr/bin/env bash
# ============================================================================
# Kimi Talk Widget — dev/test environment bootstrap
#
# Reproduces the full toolchain from scratch:
#   1. Temurin JDK 17        (javac, keytool, jarsigner stack)
#   2. Android cmdline-tools (sdkmanager)
#   3. Android SDK platform android-35 + build-tools 35.0.0
#      (aapt2, d8, zipalign, apksigner)
#   4. JUnit 4.13.2 + Hamcrest 1.3 (unit tests run on the JVM, off-device)
#
# Usage:   ./env/setup.sh [install-root]     (default: ~/kimi-talk-toolchain)
# After:   export KIMI_TALK_TOOLCHAIN=<install-root>
# ============================================================================
set -euo pipefail

ROOT="${1:-$HOME/kimi-talk-toolchain}"
mkdir -p "$ROOT/sdk/cmdline-tools" "$ROOT/testlibs"
cd "$ROOT"

echo "==> [1/4] JDK 17"
if command -v javac >/dev/null 2>&1; then
    echo "    javac already on PATH ($(javac -version 2>&1)) — skipping download"
else
    curl -sL --retry 3 -o jdk17.tar.gz \
        "https://api.adoptium.net/v3/binary/latest/17/ga/linux/x64/jdk/hotspot/normal/eclipse"
    tar xzf jdk17.tar.gz && mv jdk-17* jdk && rm jdk17.tar.gz
    export JAVA_HOME="$ROOT/jdk"
    export PATH="$JAVA_HOME/bin:$PATH"
    echo "    installed: $(javac -version 2>&1)"
fi

echo "==> [2/4] Android command-line tools"
curl -sL --retry 3 -o cmdline-tools.zip \
    "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
rm -rf sdk/cmdline-tools/latest
unzip -q -o cmdline-tools.zip -d sdk/cmdline-tools
mv sdk/cmdline-tools/cmdline-tools sdk/cmdline-tools/latest
rm cmdline-tools.zip

echo "==> [3/4] Android platform 35 + build-tools 35.0.0"
# NOTE: `yes` is killed by SIGPIPE once sdkmanager stops reading answers,
# which trips `set -o pipefail` with exit 141 even on success — so this
# pipeline is checked manually.
set +e
yes | sdk/cmdline-tools/latest/bin/sdkmanager --sdk_root="$ROOT/sdk" \
    "platforms;android-35" "build-tools;35.0.0" >/dev/null 2>&1
rc=$?
set -e
if [ "$rc" -ne 0 ] && [ "$rc" -ne 141 ]; then
    echo "    sdkmanager failed (exit $rc)"; exit "$rc"
fi
echo "    installed under $ROOT/sdk"

echo "==> [4/4] JUnit + Hamcrest"
curl -sL --retry 3 -o testlibs/junit-4.13.2.jar \
    "https://repo1.maven.org/maven2/junit/junit/4.13.2/junit-4.13.2.jar"
curl -sL --retry 3 -o testlibs/hamcrest-core-1.3.jar \
    "https://repo1.maven.org/maven2/org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar"

echo ""
echo "Done. Now run:"
echo "    export KIMI_TALK_TOOLCHAIN=\"$ROOT\""
echo "    ./tools/test.sh    # unit tests"
echo "    ./tools/build.sh   # produce dist/KimiAccessWidget-debug.apk"
