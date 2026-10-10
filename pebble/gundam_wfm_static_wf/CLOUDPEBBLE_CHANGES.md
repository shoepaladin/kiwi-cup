# CloudPebble change guide: everything since the original watchface

Covers all edits made on branch `babel-claude/magical-cori-h3t34x`:
five new suits (RX78, Victory 2, Zaku II, GM Sniper II, Kshatriya), enlarged
Aerial / Byarlant / GP02A / Qubeley, round-safe chalk sprites, the new Gundam Xi
art, and 16-colour sprites for six suits.

**Line numbers are for the ORIGINAL files** (the repo before the first commit
on this branch). Within each file, make the edits **bottom to top** so the
numbers above stay valid. Find the lines by the quoted text if your copy has
drifted.

Only 3 files have code changes: `src/c/main.c`, `src/pkjs/config.js` and
(through the Resources panel) `package.json`. Everything else is images.

---

## 1. `src/c/main.c` (2 edits)

### Edit A, `load_sprite()` switch: insert after line 144 (do this first)

Before (lines 144-146):
```c
144:     case SPRITE_SET_CROSSBONE: res = RESOURCE_ID_CROSSBONE_IDLE; break;
145:     case SPRITE_SET_AERIAL:
146:     default:                   res = RESOURCE_ID_AE_IDLE;        break;
```
After (5 new lines after 144; the old 145-146 become 150-151 in your file):
```c
    case SPRITE_SET_CROSSBONE: res = RESOURCE_ID_CROSSBONE_IDLE; break;
    case SPRITE_SET_RX78:      res = RESOURCE_ID_RX78_IDLE;      break;
    case SPRITE_SET_VICTORY2:  res = RESOURCE_ID_VICTORY2_IDLE;  break;
    case SPRITE_SET_ZAKU2:     res = RESOURCE_ID_ZAKU2_IDLE;     break;
    case SPRITE_SET_GMSNIPER2: res = RESOURCE_ID_GMSNIPER2_IDLE; break;
    case SPRITE_SET_KSHATRIYA: res = RESOURCE_ID_KSHATRIYA_IDLE; break;
    case SPRITE_SET_AERIAL:
    default:                   res = RESOURCE_ID_AE_IDLE;        break;
```

### Edit B, `SpriteSet` enum: lines 36-38

Before:
```c
36:   SPRITE_SET_CROSSBONE = 8,
37: 
38:   SPRITE_SET_LAST      = SPRITE_SET_CROSSBONE,  // clamp bound; bump with the list
```
After:
```c
  SPRITE_SET_CROSSBONE = 8,
  SPRITE_SET_RX78      = 9,
  SPRITE_SET_VICTORY2  = 10,
  SPRITE_SET_ZAKU2     = 11,
  SPRITE_SET_GMSNIPER2 = 12,
  SPRITE_SET_KSHATRIYA = 13,

  SPRITE_SET_LAST      = SPRITE_SET_KSHATRIYA,  // clamp bound; bump with the list
```
Forgetting the `SPRITE_SET_LAST` change is the easy mistake: the settings
handler clamps any value above it, so the five new suits would all silently
load as Crossbone (8).

---

## 2. `src/pkjs/config.js` (1 edit)

Lines 23-31 (the `options` list). Line 31 needs a trailing comma.

Before:
```js
31:           { "label": "Crossbone Full Cloth", "value": "8" }
```
After:
```js
          { "label": "Crossbone Full Cloth", "value": "8" },
          { "label": "RX78",                 "value": "9" },
          { "label": "Victory 2",            "value": "10" },
          { "label": "Zaku II",              "value": "11" },
          { "label": "GM Sniper II",         "value": "12" },
          { "label": "Kshatriya",            "value": "13" }
```
Gundam Xi stays value `3`. Its art was replaced; there is no code change for it.

---

## 3. Resources (`package.json` `resources.media`)

CloudPebble writes this file for you when you add resources in the
**Resources** panel, so do it there: **Add new resource → Bitmap**, upload the
file, set the **identifier**, and attach the `~chalk` file as the chalk variant
if the panel offers a platform variant.

Five new bitmap resources, identifiers exactly:

| Identifier | Base file | Chalk file |
|---|---|---|
| `RX78_IDLE` | `rx78_idle.png` | `rx78_idle~chalk.png` |
| `VICTORY2_IDLE` | `victory2_idle.png` | `victory2_idle~chalk.png` |
| `ZAKU2_IDLE` | `zaku2_idle.png` | `zaku2_idle~chalk.png` |
| `GMSNIPER2_IDLE` | `gmsniper2_idle.png` | `gmsniper2_idle~chalk.png` |
| `KSHATRIYA_IDLE` | `kshatriya_idle.png` | `kshatriya_idle~chalk.png` |

If you edit the JSON directly instead: insert these five blocks between line 84
(`},` closing `CROSSBONE_IDLE`) and line 85 (the `{` that opens the
`FONT_BLACKOPS_42` entry), one per row above:
```json
                {
                    "file": "images/rx78_idle.png",
                    "name": "RX78_IDLE",
                    "targetPlatforms": null,
                    "type": "bitmap"
                },
```

---

## 4. Image files (26 total)

Source: `resources/images/` on this branch. **ADD** = new file, **REPLACE** = same
name as an existing one (keep the identifier). Those marked 16c are 16-colour
(see section 5).

| File | Action | Size | |
|---|---|---|---|
| `rx78_idle.png` / `~chalk` | ADD | 107x130 / 82x100 | |
| `victory2_idle.png` / `~chalk` | ADD | 200x128 / 125x80 | |
| `zaku2_idle.png` / `~chalk` | ADD | 135x130 / 103x100 | 16c |
| `gmsniper2_idle.png` / `~chalk` | ADD | 98x130 / 76x100 | |
| `kshatriya_idle.png` / `~chalk` | ADD | 128x130 / 99x100 | |
| `ae_idle.png` | REPLACE | 180x130 | enlarged |
| `ae_idle~chalk.png` | ADD (chalk variant of `AE_IDLE`) | 119x86 | |
| `gpo2a_idle.png` | REPLACE | 200x123 | enlarged |
| `gpo2a_idle~chalk.png` | ADD (chalk variant of `GPO2A_IDLE`) | 136x84 | |
| `byarlant_idle.png` | REPLACE | 175x130 | enlarged, 16c |
| `byarlant_idle~chalk.png` | ADD (chalk variant of `BYARLANT_IDLE`) | 135x100 | 16c |
| `qubeley_idle.png` / `~chalk` | REPLACE | 181x130 / 127x91 | enlarged, 16c |
| `xi_idle.png` / `~chalk` | REPLACE | 155x130 / 109x91 | new Xi art |
| `sazabi_idle.png` / `~chalk` | REPLACE | 141x130 / 108x100 | 16c |
| `zeta_idle.png` / `~chalk` | REPLACE | 166x130 / 115x90 | 16c |
| `crossbone_idle.png` / `~chalk` | REPLACE | 137x130 / 106x100 | 16c |

Unchanged: Calibarn (`cb_idle*`), so nothing to upload for it.

---

## 5. The 16-colour sprites

Six suits (Sazabi, Zeta, Zaku II, Qubeley, Crossbone, Byarlant), emery and
chalk: 12 files. Each is an indexed PNG with 15 colours plus transparent,
stored at 4 bits per pixel instead of 8.

Estimated bitmap memory, emery: **269 KB to 210 KB**. Chalk: **108 KB**.
Those are my estimates (width x height x bytes per pixel), not SDK output.

Things to check in the CloudPebble build:

1. **Resource size.** The build log reports it. Compare against what I
   estimated. I don't know the exact limit for emery.
2. **4-bit storage.** The SDK should pick the 4-bit palette format for these
   automatically. If a resource's panel shows a **memory format** setting, leave
   it on the smallest option; do not force 8-bit. I have not verified this
   against the SDK.
3. **Look at the six in the emulator.** The fix if one looks wrong is to put
   its old 8-bit PNG back (see git history of `resources/images/`).

To rebuild one: `python tools/reduce_palette.py SOURCE.png OUT.png WxH`
(`tools/reduce_palette.py`). For Sazabi, use the shipped sprite as the source,
since it was recoloured after import.

---

## 6. Order that avoids build errors

1. Upload all images (section 4) and create the five resources (section 3).
2. Edit `main.c` (section 1), then `config.js` (section 2).
3. Build for **emery** and **chalk**; fix any resource-size error first.
4. In the config page, pick each new suit and check it renders.
