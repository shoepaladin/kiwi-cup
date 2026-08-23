# HANDOFF — continuing Kimi Access in a fresh session

Everything needed to pick this project up cold. Read `README.md` first for
the product/architecture overview; this file is the operational memory.

## Current state (v0.2.3, 2026-08-23)

- Lives at `kiwi-cup/kimi-access`. Two widgets ship: **Projects 2×1** (K
  left, up to two project pills right, ⋮ opens settings) and **Mic 1×1**
  (single K button, pinned to exactly one grid cell).
- 43/43 JVM unit tests pass; debug-signed APK builds cleanly.
- Package id stays `app.kimitalk.widget` (pre-rename) so existing installs
  update in place. Display name is "Kimi Access".

## Environment gotchas (learned the hard way)

- **`/tmp` is wiped between sessions.** The toolchain does not survive.
  Re-run `./env/setup.sh /tmp/toolchain` and
  `export KIMI_TALK_TOOLCHAIN=/tmp/toolchain` at the start of every session.
- **Big downloads fail on the sandbox's persistent mount** (curl exit 23).
  Keep the toolchain and build intermediates in `/tmp`:
  `KIMI_TALK_BUILD_DIR=/tmp/ktw-build ./tools/build.sh`.
- **`yes | sdkmanager` trips pipefail with exit 141 (SIGPIPE)** even on
  success — setup.sh already handles this; don't "fix" it back.
- **d8's `@argfile` reader chokes on FUSE mounts** — build.sh passes class
  files as direct arguments; keep it that way.

## Signing

- Debug keystore: `keystore/debug.keystore`, alias `kimitalk`,
  store/key password `android`. Generated on first build if missing.
- **The keystore is gitignored and not in the repo.** Losing it means the
  next APK won't install as an in-place update — users must uninstall first.
  If you still have the old keystore, keep it safe; otherwise accept the
  one-time reinstall. Current cert SHA-256:
  `52:b2:94:55:22:63:be:4f:08:a1:81:84:16:2e:c1:34:c4:0a:e5:9b:d0:42:27:9c:79:4e:a2:95:35:e5:ad:59`.

## Known behavior notes (verified on-device)

- Some launchers stretch a 1×1 widget despite perfect metadata — that is
  launcher-side grid behavior, not a bug here. The metadata (40dp min,
  40dp minResize, targetCell 1×1, resizeMode none) is pinned by
  `LayoutContractTest`.
- Both widgets must keep **distinct picker labels and previews**
  (`ManifestTest`/`LayoutContractTest` enforce this) — identical labels once
  made users add the wrong variant.
- Widget layouts must use **RemoteViews-safe classes only**; a plain
  `<View>` once caused "couldn't add widget". The allowlist test guards it.

## Where things stand / next steps

- The About popup repo link already points at
  `https://github.com/shoepaladin/kiwi-cup/tree/main/kimi-access`.
- Release packaging (a GitHub Release with the APK) is intentionally not
  done yet — the root README says "package release incoming".
- Roadmap: true in-widget voice (on-device `SpeechRecognizer` + user's own
  Moonshot API key + `TextToSpeech`), Quick Settings tile, Wear OS tile,
  Jetpack Glance rewrite, release signing + CI, F-Droid/Play.
- If Moonshot ever documents a real voice deep link, it's a one-line change
  in `LaunchActivity`.

## Quick start

```bash
./env/setup.sh /tmp/toolchain
export KIMI_TALK_TOOLCHAIN=/tmp/toolchain
./tools/test.sh                                    # 43 tests, JVM only
KIMI_TALK_BUILD_DIR=/tmp/ktw-build ./tools/build.sh
adb install -r dist/KimiAccessWidget-debug.apk
```
