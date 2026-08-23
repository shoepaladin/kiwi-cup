#!/usr/bin/env bash
# ============================================================================
# Runs the unit test suite on a desktop JVM (no emulator or device needed).
# Only Android-free classes are compiled here — see TargetResolver's javadoc
# for the design rationale.
# ============================================================================
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TC="${KIMI_TALK_TOOLCHAIN:-/tmp/toolchain}"
JUNIT="$TC/testlibs/junit-4.13.2.jar"
HAMCREST="$TC/testlibs/hamcrest-core-1.3.jar"
OUT="$ROOT/build/test"

for f in "$JUNIT" "$HAMCREST"; do
    [ -f "$f" ] || { echo "Missing $f — run env/setup.sh first."; exit 1; }
done

rm -rf "$OUT"
mkdir -p "$OUT"

echo "==> Compiling sources under test"
javac -encoding UTF-8 -cp "$JUNIT:$HAMCREST" -d "$OUT" \
    "$ROOT/app/src/main/java/app/kimitalk/widget/KimiTargets.java" \
    "$ROOT/app/src/main/java/app/kimitalk/widget/TargetResolver.java" \
    "$ROOT/app/src/main/java/app/kimitalk/widget/SettingsKeys.java" \
    "$ROOT/app/src/main/java/app/kimitalk/widget/VersionInfo.java" \
    "$ROOT"/app/src/test/java/app/kimitalk/widget/*.java

echo "==> Running JUnit"
cd "$ROOT"   # ManifestTest resolves the manifest relative to the repo root
java -cp "$OUT:$JUNIT:$HAMCREST" org.junit.runner.JUnitCore \
    app.kimitalk.widget.TargetResolverTest \
    app.kimitalk.widget.ManifestTest \
    app.kimitalk.widget.LayoutContractTest \
    app.kimitalk.widget.SettingsContractTest
