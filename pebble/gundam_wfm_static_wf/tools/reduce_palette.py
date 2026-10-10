#!/usr/bin/env python3
"""
Reduce a sprite to a 16-colour palette (4 bits per pixel on the watch)
======================================================================

Usage
-----
  python reduce_palette.py SOURCE.png OUT.png WxH            # 15 colours + transparent
  python reduce_palette.py SOURCE.png OUT.png WxH --flat     # no dithering
  python reduce_palette.py SOURCE.png OUT.png WxH --colors 8 # fewer (no extra saving)

Why this exists
---------------
fit_sprite.py writes 8-bit ARGB2222 sprites: 1 byte per pixel, whatever the
number of colours. Pebble only stores bitmaps at 1, 2, 4 or 8 bits per pixel,
so the only size drop worth having is getting a sprite down to 16 palette
entries (one of them transparent), which the SDK stores at 4 bits per pixel:
half the memory. A 42- or 32-colour palette saves nothing; it still needs 8 bits.

What it does
------------
1. Scale SOURCE (trimmed to its opaque bounds) to WxH with Lanczos, filtered
   on premultiplied alpha, like resize_sprite.py. SOURCE should be the
   full-colour art (tools/originals), not an already-quantized sprite -- except
   for a suit that was recoloured after import (Sazabi), where the shipped
   sprite is the source of truth.
2. Snap to the watch's 64 colours (ARGB2222, levels 0/85/170/255).
3. Choose the 15 palette entries from those 64: weighted k-medoids in CIELAB,
   with colour weight = sqrt(pixel count) so a small bright detail (an eye, a
   beam) is not outvoted by a large flat area. Entries are real watch colours,
   so nothing is re-mapped on the device.
4. Map every pixel to the nearest entry, with damped Floyd-Steinberg dithering
   (60% error diffusion; full diffusion is noisy at 15 colours). --flat skips it.
5. Alpha is 1-bit: opaque or transparent (one palette entry).
6. Save as an indexed PNG (transparent entry first, via tRNS) so the SDK sees
   at most 16 colours and can pick its 4-bit palette format.

Needs Pillow and NumPy.
"""

import sys

import numpy as np
from PIL import Image


def lab(rgb):
    """sRGB (0-255, ...x3) -> CIELAB."""
    c = rgb / 255.0
    c = np.where(c > 0.04045, ((c + 0.055) / 1.055) ** 2.4, c / 12.92)
    m = np.array([[.4124, .3576, .1805], [.2126, .7152, .0722],
                  [.0193, .1192, .9505]])
    xyz = c @ m.T / np.array([.9505, 1, 1.089])
    f = np.where(xyz > 0.008856, np.cbrt(xyz), 7.787 * xyz + 16 / 116)
    return np.stack([116 * f[..., 1] - 16, 500 * (f[..., 0] - f[..., 1]),
                     200 * (f[..., 1] - f[..., 2])], -1)


def snap(a):
    """Snap to the watch's four levels per channel."""
    return (np.clip(np.round(np.asarray(a, float) / 85.0), 0, 3) * 85
            ).astype(np.uint8)


def choose_palette(snapped, k):
    """k representative colours out of the (<=64) colours present."""
    cols, cnt = np.unique(snapped.reshape(-1, 3), axis=0, return_counts=True)
    if len(cols) <= k:
        return cols
    lb = lab(cols.astype(float))
    w = np.sqrt(cnt.astype(float))
    sel = [int(np.argmax(w))]
    for _ in range(k - 1):                       # weighted farthest-point start
        d = np.min(np.linalg.norm(lb[:, None] - lb[sel][None], axis=2), axis=1)
        sel.append(int(np.argmax(d * w)))
    for _ in range(20):                          # weighted k-medoids
        asg = np.linalg.norm(lb[:, None] - lb[sel][None], axis=2).argmin(1)
        new = []
        for j in range(k):
            m = np.flatnonzero(asg == j)
            if len(m) == 0:
                new.append(sel[j])
                continue
            cost = [(w[m] * np.linalg.norm(lb[m] - lb[i], axis=1)).sum()
                    for i in m]
            new.append(int(m[int(np.argmin(cost))]))
        if new == sel:
            break
        sel = new
    return cols[sel]


def reduce_palette(src, size, colors=16, dither=True):
    """Returns an indexed PIL image: index 0 transparent, then colors-1 colours."""
    im = Image.open(src).convert('RGBA')
    im = im.crop(im.getchannel('A').getbbox())
    im = im.convert('RGBa').resize(size, Image.LANCZOS).convert('RGBA')
    a = np.asarray(im)
    op = a[:, :, 3] >= 128
    rgb = a[:, :, :3].astype(float)
    pal = choose_palette(snap(rgb)[op], colors - 1)
    pl = lab(pal.astype(float))
    h, w = op.shape
    idx = np.zeros((h, w), np.uint8)             # 0 = transparent
    if dither:
        buf = rgb.copy()
        for y in range(h):
            for x in range(w):
                if not op[y, x]:
                    continue
                old = buf[y, x]
                i = int(np.linalg.norm(pl - lab(np.clip(old, 0, 255)),
                                       axis=1).argmin())
                idx[y, x] = i + 1
                err = old - pal[i]
                for dx, dy, f in ((1, 0, 7 / 16), (-1, 1, 3 / 16),
                                  (0, 1, 5 / 16), (1, 1, 1 / 16)):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < w and ny < h:
                        buf[ny, nx] += err * f * 0.6
    else:
        d = np.linalg.norm(lab(snap(rgb).astype(float))[..., None, :] - pl,
                           axis=3)
        idx = np.where(op, d.argmin(2) + 1, 0).astype(np.uint8)
    out = Image.fromarray(idx, 'P')
    flat = [0, 0, 0] + [int(v) for c in pal for v in c]
    out.putpalette(flat)            # exact length: padding to 256 makes PIL write 8-bit
    out.info['transparency'] = 0
    return out


def main(argv):
    args = [a for a in argv[1:] if not a.startswith('--')]
    colors = 16
    if '--colors' in argv:
        colors = int(argv[argv.index('--colors') + 1])
        args = [a for a in args if a != str(colors)]
    if len(args) != 3 or 'x' not in args[2].lower():
        print(__doc__.strip())
        return 2
    src, dst, spec = args
    w, h = (int(v) for v in spec.lower().split('x', 1))
    img = reduce_palette(src, (w, h), colors, '--flat' not in argv)
    img.save(dst, 'PNG', optimize=True, transparency=0)
    print('%s: %dx%d, %d palette entries (index 0 transparent)'
          % (dst, w, h, colors))
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv))
