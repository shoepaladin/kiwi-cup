#!/usr/bin/env python3
"""
Fit an existing suit artwork to the watchface sprite bands
==========================================================

For suit art that already exists as a file. This does NOT redraw anything --
it only trims transparent padding and scales, so the source artwork survives
intact. Pair it with dechecker.py when the source is a flattened JPEG whose
transparency needs restoring first.

Usage
-----
  python fit_sprite.py SOURCE.png NAME        # writes NAME_idle.png + ~chalk
  python fit_sprite.py SOURCE.png NAME --outdir /tmp/preview
  python fit_sprite.py SOURCE.png NAME --no-trim          # keep source padding
  python fit_sprite.py SOURCE.png NAME --no-round-safe    # allow round clipping
  python fit_sprite.py SOURCE.png NAME --no-quantize      # keep full colour

What it does, in order
----------------------
1. Trim the fully-transparent border, so the art sits flush. main.c aligns
   the BitmapLayer to the bottom of the band, so leftover padding would lift
   the suit off the baseline every other suit stands on.
2. Scale down (never up) to fit inside the band, preserving aspect ratio, so
   proportions are untouched. Lanczos, premultiplied, via resize_sprite.py.
3. Write the emery bitmap and the ~chalk variant the round watch picks up
   automatically by filename suffix. The chalk variant is shrunk further if
   needed to stay inside the round display's visible circle -- the band is a
   rectangle but the glass is not.
4. Quantize to the watch's ARGB2222 colour space with dithering. The watch
   does this anyway; doing it here picks the dithering deliberately and keeps
   the resource bundle well under the 256 KB platform limit.

Band sizes come from src/c/main.c: the text stack is anchored to the bottom
of the screen and the sprite band is whatever is left above it, minus the
reserved top bezel. That is 200x130 on emery and 100 tall on chalk. A bitmap
taller than its band is silently clipped by the BitmapLayer, so the suit
would lose its head into the bezel. Trim the artwork, never the bezel.

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

EMERY_MAX_W, EMERY_MAX_H = 200, 130
CHALK_MAX_W, CHALK_MAX_H = 160, 100
# Chalk is round: 180px across, and main.c starts the sprite band 4px down.
# The band is a rectangle but the glass is a circle, so a wide suit scaled to
# the full band height hangs off the edge -- see fit_round().
CHALK_SCREEN, CHALK_BAND_TOP = 180, 4


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


# Pebble's GColor8 is ARGB2222: two bits per channel, so each of R, G, B and
# A can only be one of these four levels. The watch quantizes to this whether
# we do or not -- doing it here means we choose the dithering instead of
# leaving it to the SDK, and the PNGs shrink enormously as a side effect
# (165 KB down to ~49 KB across the three suits), which is what keeps the
# resource bundle under the 256 KB platform limit.
LEVELS = (0, 85, 170, 255)


def _snap(v):
    if v < 43:
        return 0
    if v < 128:
        return 85
    if v < 213:
        return 170
    return 255


def quantize_pebble(w, h, px):
    """Snap to ARGB2222 with Floyd-Steinberg dithering on the colour channels.

    Without dithering the 64-colour palette shifts whole regions off-hue --
    Sazabi's crimson turns magenta, for one -- because a flat area all rounds
    the same way. Diffusing the error keeps the average colour right.

    Alpha is snapped without dithering: dithered alpha turns a clean sprite
    edge into a dashed one.
    """
    buf = [float(v) for v in px]
    out = bytearray(len(px))

    def diffuse(o, c, err, x, y):
        for dx, dy, frac in ((1, 0, 7.0 / 16), (-1, 1, 3.0 / 16),
                             (0, 1, 5.0 / 16), (1, 1, 1.0 / 16)):
            nx, ny = x + dx, y + dy
            if 0 <= nx < w and ny < h:
                buf[(ny * w + nx) * 4 + c] += err * frac

    for y in range(h):
        for x in range(w):
            o = (y * w + x) * 4
            alpha = _snap(px[o + 3])
            out[o + 3] = alpha
            if alpha == 0:
                continue          # colour under a transparent pixel is unused
            for c in range(3):
                old = buf[o + c]
                new = _snap(old)
                out[o + c] = new
                diffuse(o, c, old - new, x, y)
    return out


def opaque_profile(w, h, px):
    """Per source row, the first and last opaque column. Rows with nothing
    opaque are dropped. This is where a sprite can poke outside the round
    display, so it is all the circle test needs."""
    rows = []
    for y in range(h):
        base = y * w * 4
        x0 = x1 = -1
        for x in range(w):
            if px[base + x * 4 + 3]:
                if x0 < 0:
                    x0 = x
                x1 = x
        if x0 >= 0:
            rows.append((y, x0, x1))
    return rows


def fits_circle(profile, w, h, dw, dh, screen, band_top, band_h):
    """Would this scaled size stay inside the round display's visible circle?

    main.c centres the bitmap horizontally and aligns it to the bottom of the
    band, so the position follows from the size alone.
    """
    r = screen / 2.0
    ox = (screen - dw) // 2
    oy = band_top + (band_h - dh)
    for (y, x0, x1) in profile:
        ty = oy + y * float(dh) / h
        for sx in (x0, x1):
            tx = ox + sx * float(dw) / w
            if ((tx + 0.5 - r) ** 2 + (ty + 0.5 - r) ** 2) > r * r:
                return False
    return True


def fit_round(profile, w, h, max_w, max_h, screen, band_top, floor=0.7):
    """Largest size that fits the band AND stays inside the visible circle.

    A round screen is only ~65px wide at the top of the sprite band, so a
    wide suit scaled to the full band height hangs out past the glass. Shrink
    until it does not. Bounded by `floor` so a pathological source cannot
    shrink to nothing -- it just fits the band as best it can.
    """
    dw, dh = fit(w, h, max_w, max_h)
    best = (dw, dh)
    while dh >= int(max_h * floor):
        if fits_circle(profile, w, h, dw, dh, screen, band_top, max_h):
            return dw, dh, True
        dh -= 1
        dw = max(1, int(round(w * float(dh) / h)))
    return best[0], best[1], False


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
    profile = None
    try:
        for suffix, mw, mh, label in (('', EMERY_MAX_W, EMERY_MAX_H, 'emery'),
                                      ('~chalk', CHALK_MAX_W, CHALK_MAX_H,
                                       'chalk')):
            note = ''
            if suffix == '~chalk' and '--no-round-safe' not in argv:
                if profile is None:
                    profile = opaque_profile(w, h, px)
                dw, dh, clean = fit_round(profile, w, h, mw, mh,
                                          CHALK_SCREEN, CHALK_BAND_TOP)
                full = fit(w, h, mw, mh)
                if (dw, dh) != full:
                    note = '  (shrunk from %dx%d to clear the round bezel)' % full
                elif not clean:
                    note = '  (still clips the round bezel)'
            else:
                dw, dh = fit(w, h, mw, mh)
            dst = os.path.join(outdir, '%s_idle%s.png' % (name, suffix))
            if (dw, dh) == (w, h):
                write_rgba(dst, w, h, px)
                print("  %-5s %dx%d (already fits)%s" % (label, dw, dh, note))
            else:
                resize_file(trimmed, dst, dw, dh)
                if note:
                    print(" %s" % note.strip())
            assert dw <= mw and dh <= mh

            # Quantize after scaling, never before: resampling a quantized
            # image reintroduces colours that are not on the watch.
            if '--no-quantize' not in argv:
                qw, qh, qpx = read_rgba(dst)
                before = os.path.getsize(dst)
                write_rgba(dst, qw, qh, quantize_pebble(qw, qh, qpx))
                print("        ARGB2222 + dither: %.1f KB -> %.1f KB"
                      % (before / 1024.0, os.path.getsize(dst) / 1024.0))
    finally:
        if os.path.exists(trimmed):
            os.remove(trimmed)
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv))
