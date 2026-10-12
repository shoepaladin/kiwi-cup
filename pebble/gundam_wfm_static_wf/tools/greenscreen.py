#!/usr/bin/env python3
"""
Remove a green-screen background from suit art
==============================================

Usage
-----
  python greenscreen.py IN.png OUT.png
  python greenscreen.py IN.png OUT.png --hi 60 --lo 20 --min-part 0.005

For art delivered on a flat chroma-green background rather than a baked-in
checkerboard (for those, use dechecker.py). Output is trimmed RGBA, ready for
fit_sprite.py / reduce_palette.py.

How it works
------------
"Greenness" of a pixel is g - max(r, b): how far green stands above the other
two channels. The backgrounds measure ~120-240 on it; none of the suit colours
this tool has been used on go above ~15 (cyan sensors have b close to g).

1. Core: every pixel with greenness > --hi is background. This is global, not
   a flood fill, so pockets enclosed by the suit (between a rifle and an arm)
   are removed too. That is only safe because the suit itself has no green;
   do not use it on a green suit (Zaku II, Kshatriya).
2. Halo: JPEG and anti-aliasing leave a ring of green-tinted pixels. Pixels
   within 2 px of the core with greenness > --lo are background as well.
3. Despill: surviving pixels within 2 px of the background that still lean
   green have g clamped to max(r, b), so the outline does not glow green.
4. Specks: opaque islands smaller than --min-part of the largest part go.

Needs Pillow, NumPy and SciPy.
"""

import sys

import numpy as np
from PIL import Image
from scipy import ndimage


def greenscreen(path, hi=60, lo=20, near=2, min_part=0.005):
    rgb = np.asarray(Image.open(path).convert('RGB')).astype(np.int16)
    g = rgb[:, :, 1] - np.maximum(rgb[:, :, 0], rgb[:, :, 2])

    background = g > hi
    ring = ndimage.binary_dilation(background, iterations=near)
    background |= ring & (g > lo)

    edge = ndimage.binary_dilation(background, iterations=near) & ~background
    spill = edge & (g > 0)
    rgb[spill, 1] = np.maximum(rgb[spill, 0], rgb[spill, 2])

    opaque = ~background
    parts, count = ndimage.label(opaque)
    if count > 1:
        sizes = ndimage.sum(opaque, parts, range(1, count + 1))
        keep = np.flatnonzero(sizes >= sizes.max() * min_part) + 1
        print('  parts: kept %d of %d' % (len(keep), count))
        opaque = np.isin(parts, keep)

    alpha = np.where(opaque, 255, 0).astype(np.uint8)
    out = Image.fromarray(np.dstack([rgb.astype(np.uint8), alpha]), 'RGBA')
    box = out.getbbox()
    print('  background %.1f%%, despilled %d px, trimmed to %dx%d'
          % (100 * (~opaque).mean(), int(spill.sum()),
             box[2] - box[0], box[3] - box[1]))
    return out.crop(box)


def main(argv):
    args = [a for a in argv[1:] if not a.startswith('--')]
    opts = {}
    for k in ('--hi', '--lo', '--min-part'):
        if k in argv:
            v = argv[argv.index(k) + 1]
            opts[k] = float(v)
            args = [a for a in args if a != v]
    if len(args) != 2:
        print(__doc__.strip())
        return 2
    src, dst = args
    print(src)
    greenscreen(src, hi=opts.get('--hi', 60), lo=opts.get('--lo', 20),
                min_part=opts.get('--min-part', 0.005)).save(dst)
    print('  wrote %s' % dst)
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv))
