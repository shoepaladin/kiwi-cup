#!/usr/bin/env python3
"""
Fit an existing suit artwork to the watchface sprite bands
==========================================================

For suit art that already exists as a file. This does NOT redraw anything --
it only trims transparent padding and scales, so the source artwork survives
intact. (make_sprites.py is the opposite: it draws sprites from scratch.)

Usage
-----
  python fit_sprite.py SOURCE.png NAME        # writes NAME_idle.png + ~chalk
  python fit_sprite.py SOURCE.png NAME --outdir /tmp/preview
  python fit_sprite.py SOURCE.png NAME --no-trim     # keep source padding

What it does, in order
----------------------
1. Trim the fully-transparent border, so the art sits flush. main.c aligns
   the BitmapLayer to the bottom of the band, so leftover padding would lift
   the suit off the baseline every other suit stands on.
2. Scale down (never up) to fit inside the band, preserving aspect ratio, so
   proportions are untouched. Lanczos, premultiplied, via resize_sprite.py.
3. Write the emery bitmap and the ~chalk variant the round watch picks up
   automatically by filename suffix.

Band sizes come from src/c/main.c -- see make_sprites.py for how they are
derived. Trim the artwork, never the bezel.

Note on source images
---------------------
Art with an opaque background (a rendered illustration rather than a
transparent sprite) will keep that background as a visible rectangle on the
watch, because the layout composites the sprite over the user's chosen
background color with GCompOpSet. Such a source needs its background made
transparent before it comes through here; this tool deliberately does not
guess at that, since a wrong guess eats the artwork.
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from resize_sprite import read_rgba, resize_file, write_rgba   # noqa: E402

# Same contract as make_sprites.py -- keep the two in step.
EMERY_MAX_W, EMERY_MAX_H = 200, 130
CHALK_MAX_W, CHALK_MAX_H = 160, 100


def trim(w, h, px):
    """Drop the fully transparent border. Returns (w, h, px)."""
    x0, y0, x1, y1 = w, h, -1, -1
    for y in range(h):
        row = y * w
        for x in range(w):
            if px[(row + x) * 4 + 3]:
                if x < x0:
                    x0 = x
                if x > x1:
                    x1 = x
                if y < y0:
                    y0 = y
                if y > y1:
                    y1 = y
    if x1 < 0:
        raise SystemExit("source is fully transparent -- nothing to fit")
    nw, nh = x1 - x0 + 1, y1 - y0 + 1
    if (nw, nh) == (w, h):
        return w, h, px
    out = bytearray(nw * nh * 4)
    for y in range(nh):
        src = ((y + y0) * w + x0) * 4
        dst = y * nw * 4
        out[dst:dst + nw * 4] = px[src:src + nw * 4]
    return nw, nh, out


def fit(w, h, max_w, max_h):
    """Largest size within (max_w, max_h) preserving aspect. Never upscales."""
    scale = min(float(max_w) / w, float(max_h) / h, 1.0)
    return max(1, int(round(w * scale))), max(1, int(round(h * scale)))


def main(argv):
    args = [a for a in argv[1:] if not a.startswith('--')]
    here = os.path.dirname(os.path.abspath(__file__))
    outdir = os.path.join(here, '..', 'resources', 'images')
    if '--outdir' in argv:
        outdir = argv[argv.index('--outdir') + 1]
        args = [a for a in args if a != outdir]
    if len(args) != 2:
        print(__doc__.strip())
        return 2
    src, name = args
    outdir = os.path.normpath(outdir)

    w, h, px = read_rgba(src)
    print("%s: source %dx%d" % (os.path.basename(src), w, h))

    if '--no-trim' not in argv:
        tw, th, px = trim(w, h, px)
        if (tw, th) != (w, h):
            print("  trimmed transparent border -> %dx%d" % (tw, th))
        w, h = tw, th

    # The trimmed original is the scaling source for both platforms, so the
    # round variant is never a resample of an already-resampled image.
    trimmed = os.path.join(outdir, '.%s_trimmed.png' % name)
    write_rgba(trimmed, w, h, px)
    try:
        for suffix, mw, mh, label in (('', EMERY_MAX_W, EMERY_MAX_H, 'emery'),
                                      ('~chalk', CHALK_MAX_W, CHALK_MAX_H,
                                       'chalk')):
            dw, dh = fit(w, h, mw, mh)
            dst = os.path.join(outdir, '%s_idle%s.png' % (name, suffix))
            if (dw, dh) == (w, h):
                write_rgba(dst, w, h, px)
                print("  %-5s %dx%d (already fits)" % (label, dw, dh))
            else:
                resize_file(trimmed, dst, dw, dh)
            assert dw <= mw and dh <= mh
    finally:
        if os.path.exists(trimmed):
            os.remove(trimmed)
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv))
