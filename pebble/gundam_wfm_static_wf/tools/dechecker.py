#!/usr/bin/env python3
"""
Restore transparency to sprite art that was flattened onto a checkerboard
=========================================================================

The suit art in raw_gundam_images/ arrived as JPEG. JPEG has no alpha, so the
transparency checkerboard the art was displayed against got baked in as real
pixels. This turns it back into an RGBA sprite: background transparent, art
untouched.

    python dechecker.py raw_gundam_images/sazabi.jpg tools/originals/sazabi.png

Then feed the result to fit_sprite.py, which does the scaling.

Requires pillow, numpy and scipy -- unlike the rest of tools/, which is
stdlib-only. This is a one-time art-import step, not part of the watchface
build, so the dependency never reaches anyone just compiling the watchface.

    pip install pillow numpy scipy

Why it is not just "delete the light grey pixels"
-------------------------------------------------
Every one of these three images breaks the naive approach somewhere:

* Crossbone's cream armour is 224-231 while a checker tone is 211 -- only 13
  apart. A tolerance wide enough to be comfortable eats the armour.
* Sazabi's white armour is 253-255 and one checker tone is *exactly* 255.
  No colour threshold can separate them.
* Zeta's checkerboard is high contrast (159 vs 227), so JPEG ringing along
  the cell boundaries produces a band of in-between tones matching neither.
  Those bands cut the background into isolated cells, and a flood fill from
  the border cannot reach most of it.

So the rule here is three things together, and it needs all three:

1. A TIGHT tolerance around each of the two checker tones (default 10), which
   keeps Crossbone's cream out of the mask.
2. A morphological CLOSE, which bridges the JPEG-smeared cell boundaries so
   the background becomes one connected region again. Closing only fills gaps
   narrower than the kernel, so large armour areas cannot be swallowed.
3. Removal only of background CONNECTED TO THE BORDER, which is what saves
   Sazabi's white armour: it is the same colour as the checkerboard but it is
   sealed inside the suit's black outlines, so the fill never reaches it.

Finally, opaque islands under `--min-part` of the main body are dropped as
leftover checker specks. On this art the margin is comfortable: Sazabi's two
funnel clusters are 3.9% of the body and every genuine artifact is under
0.21%, so the 1% default sits in a wide empty gap. Raise it and you start
eating funnels; that is the number to check first if a suit loses a part.
"""

import os
import sys

try:
    import numpy as np
    from PIL import Image
    from scipy import ndimage
except ImportError as exc:                                   # pragma: no cover
    raise SystemExit("dechecker.py needs pillow, numpy and scipy: "
                     "pip install pillow numpy scipy  (%s)" % exc)


def _disk(r):
    y, x = np.mgrid[-r:r + 1, -r:r + 1]
    return (x * x + y * y) <= r * r


def _close(mask, r):
    """Morphological close that treats outside-the-image as background.

    scipy erodes against a zero border, which would eat the outermost r
    pixels -- exactly the ring the flood fill needs as its seed.
    """
    padded = np.pad(mask, r, constant_values=True)
    return ndimage.binary_closing(padded, _disk(r))[r:-r, r:-r]


def checker_tones(value, is_neutral):
    """The two greys of the checkerboard, read off the image border."""
    edges = np.concatenate([value[0:8, :].ravel(), value[-8:, :].ravel(),
                            value[:, 0:8].ravel(), value[:, -8:].ravel()])
    keep = np.concatenate([is_neutral[0:8, :].ravel(),
                           is_neutral[-8:, :].ravel(),
                           is_neutral[:, 0:8].ravel(),
                           is_neutral[:, -8:].ravel()])
    hist = np.bincount(edges[keep].astype(int), minlength=256).copy()
    tones = []
    for _ in range(2):
        peak = int(hist.argmax())
        tones.append(peak)
        hist[max(0, peak - 20):peak + 21] = 0      # suppress, then take the other
    return sorted(tones)


def dechecker(path, tol=10, neutral=24, close_r=7, peel=2, min_part=0.01):
    rgb = np.asarray(Image.open(path).convert('RGB')).astype(np.int16)
    is_neutral = (rgb.max(2) - rgb.min(2)) <= neutral
    value = rgb.mean(2)

    lo, hi = checker_tones(value, is_neutral)
    seed = is_neutral & ((np.abs(value - lo) <= tol) |
                         (np.abs(value - hi) <= tol))

    background = _close(seed, close_r)
    labels, _ = ndimage.label(background)
    edge_labels = np.unique(np.concatenate([labels[0, :], labels[-1, :],
                                            labels[:, 0], labels[:, -1]]))
    background = np.isin(labels, edge_labels[edge_labels > 0])

    # JPEG leaves a halo of half-background pixels along every edge of the
    # art. Peel it, but only inwards from pixels already known to be
    # background, so the looser test can never start inside the sprite.
    for _ in range(peel):
        background |= (ndimage.binary_dilation(background) & ~background &
                       is_neutral & (value >= lo - 20) & (value <= hi + 20))

    # Drop leftover checker specks: opaque islands far smaller than the body.
    opaque = ~background
    parts, count = ndimage.label(opaque)
    if count > 1:
        sizes = ndimage.sum(opaque, parts, range(1, count + 1))
        cutoff = sizes.max() * min_part
        drop = np.isin(parts, np.flatnonzero(sizes < cutoff) + 1)
        background |= drop
        kept = int((sizes >= cutoff).sum())
        print("  parts: kept %d of %d (dropped %d specks under %.2f%% of body)"
              % (kept, count, count - kept, 100 * min_part))

    alpha = np.where(background, 0, 255).astype(np.uint8)
    out = Image.fromarray(np.dstack([rgb.astype(np.uint8), alpha]), 'RGBA')
    box = out.getbbox()
    print("  checker tones %s -> background %.1f%% of image, trimmed to %dx%d"
          % ([lo, hi], 100 * background.mean(),
             box[2] - box[0], box[3] - box[1]))
    return out.crop(box)


def main(argv):
    args = [a for a in argv[1:] if not a.startswith('--')]
    if len(args) != 2:
        print(__doc__.strip())
        return 2
    src, dst = args
    tol = 10
    if '--tol' in argv:
        tol = int(argv[argv.index('--tol') + 1])
    min_part = 0.01
    if '--min-part' in argv:
        min_part = float(argv[argv.index('--min-part') + 1])
    print("%s" % os.path.basename(src))
    img = dechecker(src, tol=tol, min_part=min_part)
    out_dir = os.path.dirname(os.path.abspath(dst))
    if out_dir and not os.path.isdir(out_dir):
        os.makedirs(out_dir)
    img.save(dst)
    print("  wrote %s" % dst)
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv))
