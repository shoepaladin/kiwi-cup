#!/usr/bin/env python3
"""Generates all raster artwork for Kimi Access with Pillow:

  * mipmap-*/ic_launcher.png      — ink rounded square, cream K, orange dot
  * drawable/ic_k.png             — the K lettermark (cream + orange, transparent)
  * drawable/ic_more.png          — three-dot overflow glyph (cream)
  * drawable/preview_mic.png      — picker preview for the 1x1 mic widget
  * drawable/preview_projects.png — picker preview for the 2x1 projects widget

Design language: flat warm-ink surfaces (no gradients), off-white content,
a single burnt-orange accent used ONLY for the K's dot — the unofficial
marker. Raster assets (not vectors) are used deliberately: RemoteViews in
home-screen widgets renders PNGs reliably on every targeted API level.
"""

import os

from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app", "src", "main", "res")

# Editorial palette — flat, warm, one accent.
INK = (35, 32, 28, 255)          # #23201C warm near-black
CREAM = (244, 240, 234, 255)     # #F4F0EA
OFFWHITE = (255, 251, 245, 255)  # #FFFBF5
BROWN = (79, 72, 62, 255)        # #4F483E
ORANGE = (245, 64, 1, 255)       # #F54001 burnt orange — the dot

LAUNCHER_SIZES = {
    "mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192,
}
ICON_SIZE = 288  # single high-res asset; ImageViews scale it down


def rounded_mask(size, radius):
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, size - 1, size - 1], radius=radius, fill=255)
    return mask


def draw_k(size, fg, dot):
    """Geometric K lettermark with a single accent dot.

    Built on a 24-unit grid with exact polygon math (no rotate-and-guess):
    a stem, two 45-degree arms meeting at the vertical midpoint, and one
    dot resting on the baseline to the right.
    """
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    u = size / 24.0
    s2 = 0.70710678  # sin/cos 45°

    # stem: x 4.0..7.2u, y 3..21u
    stem_w = 3.2 * u
    d.rounded_rectangle([4.0 * u, 3.0 * u, 4.0 * u + stem_w, 21.0 * u],
                        radius=stem_w / 2, fill=fg)

    # arms from junction J=(5.6, 12)u, width 3u, length 10.5u, ±45°
    jx, jy = 5.6 * u, 12.0 * u
    arm_w = 3.0 * u
    arm_len = 10.5 * u
    half = arm_w / 2
    for sy in (-1.0, 1.0):
        along = (s2, sy * s2)          # unit vector along the arm
        normal = (s2, -sy * s2)        # perpendicular unit vector
        ex, ey = jx + arm_len * along[0], jy + arm_len * along[1]
        pts = [
            (jx + half * normal[0], jy + half * normal[1]),
            (jx - half * normal[0], jy - half * normal[1]),
            (ex - half * normal[0], ey - half * normal[1]),
            (ex + half * normal[0], ey + half * normal[1]),
        ]
        d.polygon(pts, fill=fg)
        # rounded caps at both ends of the arm
        for (cx, cy) in ((jx, jy), (ex, ey)):
            d.ellipse([cx - half, cy - half, cx + half, cy + half], fill=fg)

    # the dot: on the baseline, clear of the lower arm
    r = 1.75 * u
    cx, cy = 18.4 * u, 19.7 * u
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=dot)
    return img


def draw_more(size, fg):
    """Vertical three-dot overflow glyph."""
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    r = max(2, round(size * 0.09))
    cx = size // 2
    for cy in (round(size * 0.20), size // 2, round(size * 0.80)):
        d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=fg)
    return img


def launcher_icon(master=512):
    icon = Image.new("RGBA", (master, master), (0, 0, 0, 0))
    ink = Image.new("RGBA", (master, master), INK)
    icon.paste(ink, (0, 0), rounded_mask(master, round(master * 0.22)))
    k = draw_k(round(master * 0.66), CREAM, ORANGE)
    offset = (master - k.width) // 2
    icon.alpha_composite(k, (offset, offset))
    return icon


def preview_mic():
    """Picker preview: ink disc with the K mark."""
    size = 192
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.ellipse([0, 0, size - 1, size - 1], fill=INK)
    k = draw_k(round(size * 0.58), CREAM, ORANGE)
    off = (size - k.width) // 2
    img.alpha_composite(k, (off, off))
    return img


def preview_projects():
    """Picker preview: ink pill — K left, two cream pills right, dots corner."""
    w, h = 440, 192
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    ImageDraw.Draw(img).rounded_rectangle([0, 0, w - 1, h - 1], radius=64, fill=INK)
    k = draw_k(96, CREAM, ORANGE)
    img.alpha_composite(k, (w // 4 - 48, h // 2 - 48))
    d = ImageDraw.Draw(img)
    d.rectangle([w // 2, 52, w // 2 + 2, h - 52], fill=(244, 240, 234, 60))
    for i in range(2):
        y0 = 34 + i * 66
        d.rounded_rectangle([w // 2 + 26, y0, w - 52, y0 + 44], radius=22, fill=OFFWHITE)
    for i in range(3):
        d.ellipse([w - 30, 16 + i * 14, w - 22, 24 + i * 14], fill=CREAM)
    return img


def main():
    for density, px in LAUNCHER_SIZES.items():
        out_dir = os.path.join(RES, "mipmap-" + density)
        os.makedirs(out_dir, exist_ok=True)
        launcher_icon().resize((px, px), Image.LANCZOS).save(
            os.path.join(out_dir, "ic_launcher.png"))

    drawable = os.path.join(RES, "drawable")
    os.makedirs(drawable, exist_ok=True)
    draw_k(ICON_SIZE, CREAM, ORANGE).save(os.path.join(drawable, "ic_k.png"))
    draw_more(ICON_SIZE, CREAM).save(os.path.join(drawable, "ic_more.png"))
    preview_mic().save(os.path.join(drawable, "preview_mic.png"))
    preview_projects().save(os.path.join(drawable, "preview_projects.png"))

    # Retired assets from earlier designs.
    for stale in ("ic_mic.png", "ic_globe.png"):
        path = os.path.join(drawable, stale)
        if os.path.exists(path):
            os.remove(path)

    print("artwork written to", RES)


if __name__ == "__main__":
    main()
