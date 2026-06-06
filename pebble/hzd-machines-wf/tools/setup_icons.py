#!/usr/bin/env python3
"""
HZD / Horizon Forbidden West Machine Icon Setup
================================================

Usage
-----
  # Generate placeholder diamond icons (no external image needed):
  python setup_icons.py

  # Extract the real icons from the Guerrilla "Machines" poster:
  python setup_icons.py path/to/hfw_machines.png

The poster (the one with the white machine silhouettes on a dark gray
background, © Guerrilla) is laid out as a grid of 21 machines:

    row 1:  Burrower  Clawstrider  Slitherfang  Fanghorn  Sunwing  Leaplasher
    row 2:  Scrounger Slaughterspine Clamberjaw Tremortusk Bristleback Rollerback
    row 3:  Shellsnapper Skydrifter Widemaw Spikesnout Plowhorn Tideripper
    row 4:  Dreadwing Specter Specter Prime

Each machine has its NAME printed in small caps beneath the silhouette.

The extractor:
  1. Thresholds the image to a white-on-black mask (silhouettes + name text).
  2. Isolates each grid row by an approximate vertical band.
  3. Splits each row into individual machines by column clustering.
  4. For each machine, separates the icon blob from the name text below it by
     cutting at the largest vertical gap (icons are a tall blob; names are a
     thin strip lower down) — so the name label is dropped automatically.
  5. Tight-crops to the silhouette, pads to a centered square, resizes, and
     saves a white-on-black PNG into ../resources/images/.

Output PNGs are white silhouettes on a black field. The watch face background
is black and uses GCompOpSet/Assign compositing, so the black field reads as
transparent — only the silhouette shows.

Requires:  pip install Pillow numpy
"""

import sys, os, struct, zlib

OUT_DIR        = os.path.join(os.path.dirname(__file__), '..', 'resources', 'images')
ICON_SIZE      = 80   # basalt / diorite (fits 82-px-wide ICON_RECT)
ICON_SIZE_EMERY = 110  # emery platform variant (fits 112-px-wide ICON_RECT)

# Order MUST match the resource order in package.json and main.c.
ICON_NAMES = [
    'burrower', 'clawstrider', 'slitherfang', 'fanghorn', 'sunwing', 'leaplasher',
    'scrounger', 'slaughterspine', 'clamberjaw', 'tremortusk', 'bristleback', 'rollerback',
    'shellsnapper', 'skydrifter', 'widemaw', 'spikesnout', 'plowhorn', 'tideripper',
    'dreadwing', 'specter', 'specter_prime',
]

# Grid layout: list of (number_of_icons_in_row) per row, top to bottom.
ROW_COUNTS = [6, 6, 6, 3]

# Approximate vertical bands per row, as (top, bottom) fractions of image height.
# These only need to separate rows from each other; the icon/name split inside
# each cell is found automatically. Generous is fine as long as bands don't
# overlap an adjacent row.
ROW_BANDS = [
    (0.250, 0.390),   # row 1
    (0.405, 0.560),   # row 2
    (0.575, 0.720),   # row 3
    (0.740, 0.880),   # row 4
]

# Horizontal span (left, right fractions of width) that the icons in each row
# occupy. Rows of 6 span nearly the full width; the 3-icon row is centered.
ROW_XSPANS = [
    (0.06, 0.94),
    (0.06, 0.94),
    (0.06, 0.94),
    (0.30, 0.70),
]


# ── Pure-Python grayscale PNG writer (used by the placeholder path) ────────

def _chunk(tag, data):
    return (struct.pack('>I', len(data)) + tag + data
            + struct.pack('>I', zlib.crc32(tag + data) & 0xFFFFFFFF))

def save_grayscale_png(path, pixels_2d):
    h = len(pixels_2d); w = len(pixels_2d[0])
    raw = b''.join(b'\x00' + bytes(row) for row in pixels_2d)
    png = (b'\x89PNG\r\n\x1a\n'
           + _chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 0, 0, 0, 0))
           + _chunk(b'IDAT', zlib.compress(raw, 9))
           + _chunk(b'IEND', b''))
    with open(path, 'wb') as f:
        f.write(png)


def save_indexed_png(path, index_2d, palette):
    """Write an 8-bit INDEXED (color-type-3) PNG. `index_2d` holds palette
    indices; `palette` is a list of (r,g,b). Pebble loads these as palettized
    bitmaps whose colors can be rewritten on-watch (used for icon tinting)."""
    h = len(index_2d); w = len(index_2d[0])
    raw  = b''.join(b'\x00' + bytes(row) for row in index_2d)
    plte = b''.join(struct.pack('BBB', *c) for c in palette)
    png = (b'\x89PNG\r\n\x1a\n'
           + _chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 3, 0, 0, 0))
           + _chunk(b'PLTE', plte)
           + _chunk(b'IDAT', zlib.compress(raw, 9))
           + _chunk(b'IEND', b''))
    with open(path, 'wb') as f:
        f.write(png)


# ── Placeholder diamond generator (no source image) ────────────────────────

def make_diamond_icon(name, size=ICON_SIZE):
    import math
    grid = [[0] * size for _ in range(size)]
    cx = cy = size // 2
    r = size // 2 - 4
    def set_px(x, y):
        if 0 <= x < size and 0 <= y < size: grid[y][x] = 255
    for i in range(-r, r + 1):
        j = r - abs(i)
        set_px(cx + i, cy - j); set_px(cx + i, cy + j)
    # inner mark seeded from the name so each placeholder looks distinct
    seed = sum(ord(c) for c in name) % 3
    if seed == 0:
        for i in range(-r // 2, r // 2 + 1):
            set_px(cx + i, cy); set_px(cx, cy + i)
    elif seed == 1:
        for i in range(-r // 2, r // 2 + 1):
            set_px(cx + i, cy + i); set_px(cx + i, cy - i)
    else:
        for a in range(360):
            set_px(int(cx + (r - 8) * math.cos(math.radians(a))),
                   int(cy + (r - 8) * math.sin(math.radians(a))))
    return grid


# ── Real-poster extractor ──────────────────────────────────────────────────

def _clusters(values, threshold, min_gap, min_width):
    """Return [(start, end)] index ranges where values>threshold, merging gaps
    shorter than min_gap and dropping ranges narrower than min_width."""
    runs = []
    start = None
    gap = 0
    for i, v in enumerate(values):
        if v > threshold:
            if start is None:
                start = i
            gap = 0
        else:
            if start is not None:
                gap += 1
                if gap > min_gap:
                    runs.append((start, i - gap + 1))
                    start = None
                    gap = 0
    if start is not None:
        runs.append((start, len(values)))
    return [(a, b) for (a, b) in runs if (b - a) >= min_width]


def extract_from_poster(sheet_path):
    try:
        from PIL import Image
        import numpy as np
    except ImportError:
        print("Need Pillow and numpy:  pip install Pillow numpy")
        sys.exit(1)

    img = Image.open(sheet_path).convert('L')
    W, H = img.size
    arr = np.array(img)

    # White silhouettes/text on dark bg -> mask. Otsu-ish: midpoint works well
    # on this high-contrast poster.
    thr = max(110, int(arr.mean() + arr.std()))
    mask = (arr > thr).astype(np.uint8)

    # Background level-stretch: the poster's dark-gray field becomes pure black
    # so the saved icon blends into the watch's black background, while the
    # bright silhouette (with its anti-aliased edges) is preserved.
    bg_level = float(np.median(arr))            # dominant dark-gray background
    floor    = bg_level + 12.0                   # small margin above background
    clean    = np.clip((arr.astype(np.float32) - floor) / (255.0 - floor) * 255.0,
                       0, 255).astype(np.uint8)
    clean_img = Image.fromarray(clean, 'L')      # crop icons from this

    os.makedirs(OUT_DIR, exist_ok=True)
    idx = 0
    saved = 0

    for row_i, n_cols in enumerate(ROW_COUNTS):
        top = int(ROW_BANDS[row_i][0] * H)
        bot = int(ROW_BANDS[row_i][1] * H)
        xl  = int(ROW_XSPANS[row_i][0] * W)
        xr  = int(ROW_XSPANS[row_i][1] * W)
        band = mask[top:bot, xl:xr]

        # Column clustering to find each machine's horizontal extent.
        col_counts = band.sum(axis=0)
        col_thr    = 0
        min_gap    = int(0.012 * W)   # bridge small internal gaps in a silhouette
        min_width  = int(0.02 * W)
        xranges = _clusters(col_counts, col_thr, min_gap, min_width)

        if len(xranges) != n_cols:
            print(f"  [row {row_i+1}] detected {len(xranges)} icons, expected {n_cols} "
                  f"-- adjust ROW_BANDS/ROW_XSPANS if extraction looks off.")

        for (cx0, cx1) in xranges:
            if idx >= len(ICON_NAMES):
                break
            sub = band[:, cx0:cx1]
            row_counts = sub.sum(axis=1)

            # Separate icon (upper tall blob) from name text (lower thin strip)
            # by cutting at the largest vertical gap between non-empty runs.
            yruns = _clusters(row_counts, 0, min_gap=2, min_width=1)
            if not yruns:
                idx += 1
                continue
            # Keep everything up to the biggest gap (icon); drop the rest (name).
            icon_y0 = yruns[0][0]
            icon_y1 = yruns[0][1]
            best_gap = 0
            for k in range(len(yruns) - 1):
                gap = yruns[k + 1][0] - yruns[k][1]
                if gap > best_gap:
                    best_gap = gap
                    icon_y1 = yruns[k][1]   # icon ends at the run before the gap

            # Absolute crop box in the original image
            ax0 = xl + cx0
            ax1 = xl + cx1
            ay0 = top + icon_y0
            ay1 = top + icon_y1

            crop = clean_img.crop((ax0, ay0, ax1, ay1))

            # Pad to a centered square on black, then resize.
            cw, ch = crop.size
            side = max(cw, ch)
            square = Image.new('L', (side, side), 0)
            square.paste(crop, ((side - cw) // 2, (side - ch) // 2))

            # Saved as a 16-color INDEXED PNG (grayscale palette). Pebble keeps
            # palettized PNGs as GBitmapFormat4BitPalette, so the watch can
            # recolor the silhouette at runtime by rewriting palette entries.
            # basalt/diorite base icon
            out = square.resize((ICON_SIZE, ICON_SIZE), Image.LANCZOS).quantize(colors=16)
            out_path = os.path.join(OUT_DIR, f'icon_{ICON_NAMES[idx]}.png')
            out.save(out_path)

            # emery platform variant
            out_emery = square.resize((ICON_SIZE_EMERY, ICON_SIZE_EMERY),
                                      Image.LANCZOS).quantize(colors=16)
            out_emery_path = os.path.join(OUT_DIR, f'icon_{ICON_NAMES[idx]}~emery.png')
            out_emery.save(out_emery_path)

            print(f"  Saved: icon_{ICON_NAMES[idx]}.png ({ICON_SIZE}px)  "
                  f"icon_{ICON_NAMES[idx]}~emery.png ({ICON_SIZE_EMERY}px)  "
                  f"[crop {ax1-ax0}x{ay1-ay0} from poster]")
            idx += 1
            saved += 1

    print(f"\nExtracted {saved}/{len(ICON_NAMES)} icons into {OUT_DIR}  "
          f"({saved} base @ {ICON_SIZE}px + {saved} ~emery @ {ICON_SIZE_EMERY}px)")
    if saved != len(ICON_NAMES):
        print("If counts are off, tweak ROW_BANDS / ROW_XSPANS near the top of this file.")


# ── Main ──────────────────────────────────────────────────────────────────

def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    if len(sys.argv) > 1:
        path = sys.argv[1]
        if not os.path.exists(path):
            print(f"Error: file not found: {path}")
            sys.exit(1)
        print(f"Extracting icons from poster: {path}")
        extract_from_poster(path)
    else:
        print("No poster image provided -- generating placeholder diamond icons.")
        pal = [(0, 0, 0), (255, 255, 255)]   # index 0 = black field, 1 = silhouette
        for name in ICON_NAMES:
            base = make_diamond_icon(name, ICON_SIZE)
            save_indexed_png(os.path.join(OUT_DIR, f'icon_{name}.png'),
                             [[1 if v else 0 for v in row] for row in base], pal)
            print(f"  Created placeholder: icon_{name}.png  ({ICON_SIZE}px)")
            emery = make_diamond_icon(name, ICON_SIZE_EMERY)
            save_indexed_png(os.path.join(OUT_DIR, f'icon_{name}~emery.png'),
                             [[1 if v else 0 for v in row] for row in emery], pal)
            print(f"  Created placeholder: icon_{name}~emery.png  ({ICON_SIZE_EMERY}px)")
    print("\nDone. Then run:  python tools/create_zip.py")


if __name__ == '__main__':
    main()
