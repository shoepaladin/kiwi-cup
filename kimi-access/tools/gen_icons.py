#!/usr/bin/env python3
"""Generates the launcher icon PNGs for Kimi Access with Pillow:

  * mipmap-*/ic_launcher.png      — ink rounded square, cream K, orange dot
    (pre-API-26 fallback; API 26+ uses mipmap-anydpi-v26/ic_launcher.xml)

Everything else is a committed vector drawable (ic_k, ic_more, preview_mic,
preview_projects) and must NOT be regenerated here: emitting PNGs with the
same resource names makes aapt2 fail with "resource has a conflicting value
for configuration".

Design language: flat warm-ink surfaces (no gradients), off-white content,
a single burnt-orange accent used ONLY for the K's dot — the unofficial
marker.
"""

import os

from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app", "src", "main", "res")

# Editorial palette — flat, warm, one accent.
INK = (35, 32, 28, 255)          # #23201C warm near-black
CREAM = (244, 240, 234, 255)     # #F4F0EA
ORANGE = (245, 64, 1, 255)       # #F54001 burnt orange — the dot

LAUNCHER_SIZES = {
    "mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192,
}


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


def launcher_icon(master=512):
    icon = Image.new("RGBA", (master, master), (0, 0, 0, 0))
    ink = Image.new("RGBA", (master, master), INK)
    icon.paste(ink, (0, 0), rounded_mask(master, round(master * 0.22)))
    k = draw_k(round(master * 0.66), CREAM, ORANGE)
    offset = (master - k.width) // 2
    icon.alpha_composite(k, (offset, offset))
    return icon


def main():
    # Launcher fallbacks for pre-API-26 devices only. The adaptive icon
    # (mipmap-anydpi-v26/ic_launcher.xml) is committed and takes precedence
    # on API 26+.
    for density, px in LAUNCHER_SIZES.items():
        out_dir = os.path.join(RES, "mipmap-" + density)
        os.makedirs(out_dir, exist_ok=True)
        launcher_icon().resize((px, px), Image.LANCZOS).save(
            os.path.join(out_dir, "ic_launcher.png"))

    # Retired assets from earlier designs.
    drawable = os.path.join(RES, "drawable")
    for stale in ("ic_mic.png", "ic_globe.png",
                  # Now committed as vector drawables (.xml); a PNG twin would
                  # collide at aapt2 link time.
                  "ic_k.png", "ic_more.png",
                  "preview_mic.png", "preview_projects.png"):
        path = os.path.join(drawable, stale)
        if os.path.exists(path):
            os.remove(path)

    print("artwork written to", RES)


if __name__ == "__main__":
    main()
