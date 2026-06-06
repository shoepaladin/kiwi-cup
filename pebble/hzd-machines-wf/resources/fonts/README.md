# Custom font: Share Tech Mono

The watchface time/AM-PM uses **Share Tech Mono** — a geometric monospace font
that closely matches Horizon Zero Dawn's in-game UI style.

## Required file

Place this exact file in this folder before building:

    resources/fonts/ShareTechMono-Regular.ttf

It is referenced by `package.json` as two resources (different rasterized
sizes for the small vs. large screens):

  - `FONT_SHARE_TECH_MONO_42`  (basalt / diorite — Pebble Time, Core 2 Duo)
  - `FONT_SHARE_TECH_MONO_56`  (emery — Pebble Time 2)

Both point at the same TTF; the trailing number is the pixel height.

## Where to get it

Share Tech Mono is free under the **SIL Open Font License 1.1**.
Download from Google Fonts: https://fonts.google.com/specimen/Share+Tech+Mono

1. Click "Get font" → "Download all".
2. Unzip; find `ShareTechMono-Regular.ttf`.
3. Copy it here as `resources/fonts/ShareTechMono-Regular.ttf` (keep the name).

In CloudPebble: add it via the project's "Resources" panel as a `font`
resource, identifier `FONT_SHARE_TECH_MONO_42` (and `_56`), then it builds.

## Turning the custom font off

If you'd rather not add the TTF, the build can run on the built-in LECO font:

1. In `src/c/main.c`, set `#define USE_CUSTOM_FONT 0`.
2. In `package.json`, delete the two `"type": "font"` entries.

The face then renders the time in LECO_42 with no external font needed.

## Character set note

`characterRegex` in package.json is limited to `[0-9: APM]` so only the glyphs
the clock actually draws are baked in. This keeps the resource small. If you
extend the face to show text (date, etc.) in this font, widen that regex.
