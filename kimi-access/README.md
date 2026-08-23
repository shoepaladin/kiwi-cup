# Kimi Access Widget

A tiny, unofficial Android home-screen widget: **one button, one tap, and
you're talking to Kimi** (Moonshot AI's assistant) — plus pill-style
shortcuts to your own projects.

## The widgets

**Projects widget (2×1)** — the K mark on the left, your projects on the
right:

```
┌─────────────┬──────────────────┐
│             │               ⋮  │   ⋮ = settings
│     K·      │  ( Project 1 )   │   pills = your shortcuts
│             │  ( Project 2 )   │
└─────────────┴──────────────────┘
```

* **Left cell** — opens Kimi (follows your saved tap mode).
* **Right cell** — one or two pill-style project shortcuts you configure.
  A pill opens its link (any URL; defaults to kimi.com). With nothing
  configured, a hint pill points at settings.
* **⋮ (top-right)** — opens settings: tap mode, project slots, About.

**Mic widget (1×1)** — the minimal single-button variant, pinned to exactly
one grid cell.

The two widgets have **distinct picker labels and rendered previews**
("Projects 2×1" vs "Mic 1×1") so they can't be confused in the widget menu.

## Visual identity

Flat, warm, editorial — no gradients anywhere:

| Token | Value | Used for |
|---|---|---|
| warm ink | `#23201C` | widget surface, settings headings |
| cream | `#F4F0EA` | glyphs, settings background |
| off-white | `#FFFBF5` | project pills |
| warm brown | `#4F483E` | pill text, body copy |
| muted | `#8A7D6B` | footnotes |
| burnt orange | `#F54001` | **only** the K's dot |

The lettermark is a geometric **K with a single orange dot** — echoing
Kimi's mark, but the orange dot (instead of the official green) is the
deliberate tell that this is not an official Moonshot product. All artwork
is code-drawn in `tools/gen_icons.py` (exact 24-unit grid geometry, no
rotate-and-guess).

On Android 12+, widgets tint with your wallpaper's Material You accent
instead of ink.

## Tap routing (K / left cell)

```
            tap
             │
   ┌─────────────────┐
   │  LaunchActivity  │  saved mode (Settings)
   └─────────────────┘
             ▼
   ┌───────────────────────────────────────┐
   │          TargetResolver               │
   │  AUTO → app if installed, else web    │
   │  APP  → app first, web as fallback    │
   │  WEB  → web first, app as fallback    │
   │  ASK  → dialog if both available      │
   └───────────────────────────────────────┘
             │
   ┌─────────┼─────────────┬────────────┐
   ▼         ▼             ▼            ▼
 Kimi app  kimi.com    ask dialog    toast
(installed) (browser)  (rememberable) (nothing available)
```

## Engineering notes

* **No official voice deep link exists.** Moonshot doesn't publish a public
  intent route into voice mode, so the reliable target is the Kimi app
  itself (`com.moonshot.kimichat`). A documented deep link would be a
  one-line change in `LaunchActivity`.
* **RemoteViews discipline.** Widget layouts use only RemoteViews-safe
  classes — a plain `<View>` divider once made launchers refuse to add the
  widget entirely. `LayoutContractTest` enforces the allowlist.
* **Sizing metadata.** 1×1 means min 40dp, minResize 40dp, targetCell 1×1 —
  verified in the compiled APK. If a launcher still stretches the widget,
  it's launcher-side behavior (grid/padding settings), not the metadata.
* **Graceful degradation everywhere.** Modes reorder preference but never
  strand the user; project URLs are scheme-normalized; empty pills hide.
* **Minimal attack surface**: only the settings screen is exported.

## Repository layout

```
kimi-access/
├── app/src/main/
│   ├── AndroidManifest.xml            # 2 widget receivers, settings, trampolines
│   ├── java/app/kimitalk/widget/
│   │   ├── KimiTargets.java           # constants + URL normalization (unit-tested)
│   │   ├── TargetResolver.java        # pure routing logic + Mode (unit-tested)
│   │   ├── SettingsKeys.java          # SharedPreferences keys (unit-tested)
│   │   ├── VersionInfo.java           # version/date/repo for About (unit-tested)
│   │   ├── KimiProjectsWidgetProvider.java  # 2x1 K + project pills
│   │   ├── KimiMicWidgetProvider.java # 1x1 K-only widget
│   │   ├── WidgetStyling.java         # Material You tint (API 31+)
│   │   ├── LaunchActivity.java        # invisible trampoline (Android glue)
│   │   ├── ChooseTargetActivity.java  # "ask every time" dialog host
│   │   └── SettingsActivity.java      # modes, project slots, About popup
│   └── res/                           # layouts, widget meta, drawables, icons
├── app/src/test/java/…                # JUnit 4 tests (run on the JVM)
├── env/setup.sh                       # reproducible dev/test toolchain
├── tools/test.sh                      # compile + run unit tests
├── tools/build.sh                     # aapt2 → javac → d8 → zipalign → sign
├── tools/gen_icons.py                 # all artwork, code-drawn (K mark, ⋮, previews)
└── dist/KimiAccessWidget-debug.apk    # ready-to-sideload build
```

Note: the Java package stays `app.kimitalk.widget` so existing installs
update in place; only the display name changed to Kimi Access.

## Dev/test environment

```bash
./env/setup.sh ~/kimi-talk-toolchain
export KIMI_TALK_TOOLCHAIN=~/kimi-talk-toolchain
```

Bootstraps Temurin JDK 17, Android SDK platform 35 + build-tools 35.0.0,
and JUnit 4 into one directory.

## Unit tests

```bash
./tools/test.sh
```

43 tests run hermetically on the JVM (no emulator needed):

* **TargetResolverTest** (18) — 4-mode × availability routing matrix,
  preference parsing, constant pins.
* **ManifestTest** (7) — receivers exported/wired with **distinct labels**,
  `<queries>` visibility, sole launcher entry, non-exported internals,
  manifest versionName kept in sync with `VersionInfo`.
* **LayoutContractTest** (9) — id contracts, RemoteViews-safe class
  allowlist, widget-info → layout references, **distinct preview images
  that exist on disk**, zero background polling, sizing guards.
* **SettingsContractTest** (9) — version metadata formats, repo URL sanity,
  the "Kimi Access" name pin, distinct preference keys, URL normalization.

## Build & install

```bash
./tools/build.sh        # -> dist/KimiAccessWidget-debug.apk
adb install -r dist/KimiAccessWidget-debug.apk
```

Then long-press your home screen → **Widgets** → **Kimi Access** → pick
"Projects 2×1" or "Mic 1×1" (they now look different in the picker).

The build is Gradle-free: `aapt2 compile/link → javac → d8 → zipalign →
apksigner`. All artwork (launcher icon, K mark, picker previews) is
generated by `tools/gen_icons.py` at build time and intentionally **not
committed** — the repo carries sources only. On slow/flaky filesystems,
redirect intermediates:

```bash
KIMI_TALK_BUILD_DIR=/tmp/ktw-build ./tools/build.sh
```

## Signing

Ships signed with a local **debug** key (`keystore/debug.keystore`, password
`android`); the key is stable across builds so updates install in place.
Re-sign with a real key before publishing.

## Changelog

* **v1.0.0** — first working widget: one tap → Kimi app / kimi.com fallback.
* **v1.1.0** — 1×1 variant, settings screen with tap modes, Material You.
* **v1.1.1** — hardening: TOCTOU-safe PackageManager usage, null-intent
  guard, no duplicate dialogs, clipping fix, LayoutContractTest.
* **v1.2.0** — 2×1 projects widget (pills + ⋮ settings), About popup,
  RemoteViews `<View>` bugfix, SettingsContractTest.
* **v1.3.0** — renamed to **Kimi Access**; visual overhaul: flat warm-ink
  surfaces, off-white pills, geometric K lettermark with a single
  burnt-orange dot (the unofficial marker); distinct picker labels +
  rendered previews per widget; 1×1 sizing pinned and verified in the
  compiled binary; 43 tests.

* **v0.2.0** — first public push to kiwi-cup (`kimi-access/`); version
  aligned with project numbering (supersedes the internal 1.x line);
  About popup now links to the real repo; artwork build-generated.

## Roadmap

* **v2 — true in-widget voice**: tap → the app itself starts listening
  (on-device `SpeechRecognizer`), calls the Moonshot API with your own key,
  speaks the reply (`TextToSpeech`).
* Tier 3: Quick Settings tile, Wear OS tile, Jetpack Glance rewrite,
  release signing + CI, F-Droid/Play distribution.

## Legal

Unofficial fan project, not affiliated with or endorsed by Moonshot AI.
"Kimi" is a trademark of Moonshot AI. MIT license — see `LICENSE`.
