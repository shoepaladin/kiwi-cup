#!/usr/bin/env python3
"""
Sprite resizer for the Gundam SD watchface
==========================================

Usage
-----
  python resize_sprite.py IN.png OUT.png SIZE       # square, e.g. 130
  python resize_sprite.py IN.png OUT.png WxH        # explicit, e.g. 130x130

Why this exists
---------------
The watchface reserves a fixed empty bezel at the top of the screen (see
`top_bezel` in src/c/main.c). Any sprite taller than the remaining sprite band
would be silently clipped by the BitmapLayer -- so instead of cropping the art
(every sprite here is tightly cropped already, with zero transparent padding),
the offending sprite is scaled down to fit.

Pebble has no runtime image scaler, so this has to happen offline, at build
time. Doing it here also means the resample runs in full 8-bit-per-channel
RGBA and the SDK quantizes to ARGB2222 exactly once, which matters: Calibarn's
antenna is only ~2px wide at the tip, and a cheaper runtime nearest-neighbour
pass would drop the column it lives in.

Implementation notes
--------------------
* Lanczos-3, separable (horizontal pass then vertical pass).
* Alpha is PREMULTIPLIED before filtering and un-premultiplied afterwards.
  Filtering straight RGBA would pull the color of fully-transparent pixels
  into the edge pixels and fringe the silhouette.
* Pure `struct` + `zlib` PNG read/write so it runs with no third-party
  packages, matching tools/setup_icons.py in the hzd-machines-wf project.
  Pillow is used automatically when available (identical filter, faster).

Only 8-bit RGBA (PNG color type 6) input is supported -- that is what all six
suit sprites in resources/images/ are.
"""

import math
import os
import struct
import sys
import zlib


# ---------------------------------------------------------------- PNG decode

def _unfilter(raw, w, h, bpp, stride):
    """Reverse the per-scanline PNG filters. Returns a flat bytearray."""
    out = bytearray()
    prev = bytearray(stride)
    pos = 0
    for _ in range(h):
        ftype = raw[pos]
        pos += 1
        line = bytearray(raw[pos:pos + stride])
        pos += stride
        if ftype == 1:
            for x in range(bpp, stride):
                line[x] = (line[x] + line[x - bpp]) & 0xFF
        elif ftype == 2:
            for x in range(stride):
                line[x] = (line[x] + prev[x]) & 0xFF
        elif ftype == 3:
            for x in range(stride):
                a = line[x - bpp] if x >= bpp else 0
                line[x] = (line[x] + ((a + prev[x]) >> 1)) & 0xFF
        elif ftype == 4:
            for x in range(stride):
                a = line[x - bpp] if x >= bpp else 0
                b = prev[x]
                c = prev[x - bpp] if x >= bpp else 0
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                pr = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                line[x] = (line[x] + pr) & 0xFF
        elif ftype != 0:
            raise ValueError("unsupported PNG filter type %d" % ftype)
        out += line
        prev = line
    return out


def read_rgba(path):
    """Read an 8-bit RGBA PNG. Returns (width, height, flat bytearray)."""
    data = open(path, 'rb').read()
    if data[:8] != b'\x89PNG\r\n\x1a\n':
        raise ValueError("%s is not a PNG" % path)
    idat = b''
    hdr = None
    i = 8
    while i < len(data):
        (ln,) = struct.unpack('>I', data[i:i + 4])
        typ = data[i + 4:i + 8]
        if typ == b'IHDR':
            hdr = struct.unpack('>IIBBBBB', data[i + 8:i + 8 + 13])
        elif typ == b'IDAT':
            idat += data[i + 8:i + 8 + ln]
        elif typ == b'IEND':
            break
        i += 12 + ln
    w, h, depth, ctype, _comp, _filt, interlace = hdr
    if depth != 8 or ctype != 6:
        raise ValueError("%s: need 8-bit RGBA (depth 8, color type 6), got "
                         "depth %d type %d" % (path, depth, ctype))
    if interlace:
        raise ValueError("%s: interlaced PNG not supported" % path)
    return w, h, _unfilter(zlib.decompress(idat), w, h, 4, w * 4)


# ---------------------------------------------------------------- PNG encode

def write_rgba(path, w, h, px):
    """Write an 8-bit RGBA PNG using per-row Paeth filtering."""
    stride = w * 4
    raw = bytearray()
    prev = bytearray(stride)
    for y in range(h):
        line = px[y * stride:(y + 1) * stride]
        enc = bytearray(stride)
        for x in range(stride):
            a = line[x - 4] if x >= 4 else 0
            b = prev[x]
            c = prev[x - 4] if x >= 4 else 0
            p = a + b - c
            pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
            pr = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
            enc[x] = (line[x] - pr) & 0xFF
        raw += b'\x04' + enc
        prev = line

    def chunk(typ, payload):
        return (struct.pack('>I', len(payload)) + typ + payload +
                struct.pack('>I', zlib.crc32(typ + payload) & 0xFFFFFFFF))

    with open(path, 'wb') as f:
        f.write(b'\x89PNG\r\n\x1a\n')
        f.write(chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 6, 0, 0, 0)))
        f.write(chunk(b'IDAT', zlib.compress(bytes(raw), 9)))
        f.write(chunk(b'IEND', b''))


# ------------------------------------------------------------------ resample

def _lanczos(x, a=3.0):
    if x == 0.0:
        return 1.0
    if abs(x) >= a:
        return 0.0
    px = math.pi * x
    return (a * math.sin(px) * math.sin(px / a)) / (px * px)


def _contributions(src_len, dst_len):
    """Per-destination-pixel (start_index, [weights]) for one axis."""
    scale = float(dst_len) / float(src_len)
    fscale = 1.0 / scale if scale < 1.0 else 1.0
    support = 3.0 * fscale
    rows = []
    for i in range(dst_len):
        center = (i + 0.5) / scale
        lo = max(0, int(math.floor(center - support)))
        hi = min(src_len - 1, int(math.ceil(center + support)))
        ws = [_lanczos((x + 0.5 - center) / fscale) for x in range(lo, hi + 1)]
        total = sum(ws)
        if total != 0.0:
            ws = [v / total for v in ws]
        rows.append((lo, ws))
    return rows


def resize_rgba(w, h, px, dw, dh):
    """Lanczos-3 resample of flat RGBA bytes, filtering premultiplied alpha."""
    # Premultiply into floats.
    n = w * h
    pr = [0.0] * n
    pg = [0.0] * n
    pb = [0.0] * n
    pa = [0.0] * n
    for i in range(n):
        o = i * 4
        a = px[o + 3] / 255.0
        pr[i] = px[o] * a
        pg[i] = px[o + 1] * a
        pb[i] = px[o + 2] * a
        pa[i] = px[o + 3]

    # Horizontal pass: (w x h) -> (dw x h)
    hc = _contributions(w, dw)
    hr = [0.0] * (dw * h)
    hg = [0.0] * (dw * h)
    hb = [0.0] * (dw * h)
    ha = [0.0] * (dw * h)
    for y in range(h):
        row = y * w
        orow = y * dw
        for x in range(dw):
            lo, ws = hc[x]
            ar = ag = ab = aa = 0.0
            for k, wt in enumerate(ws):
                s = row + lo + k
                ar += pr[s] * wt
                ag += pg[s] * wt
                ab += pb[s] * wt
                aa += pa[s] * wt
            o = orow + x
            hr[o] = ar
            hg[o] = ag
            hb[o] = ab
            ha[o] = aa

    # Vertical pass: (dw x h) -> (dw x dh)
    vc = _contributions(h, dh)
    out = bytearray(dw * dh * 4)
    for y in range(dh):
        lo, ws = vc[y]
        orow = y * dw
        for x in range(dw):
            ar = ag = ab = aa = 0.0
            for k, wt in enumerate(ws):
                s = (lo + k) * dw + x
                ar += hr[s] * wt
                ag += hg[s] * wt
                ab += hb[s] * wt
                aa += ha[s] * wt
            # Un-premultiply.
            if aa <= 0.0:
                r = g = b = a = 0
            else:
                inv = 255.0 / aa
                r = int(min(255.0, max(0.0, ar * inv)) + 0.5)
                g = int(min(255.0, max(0.0, ag * inv)) + 0.5)
                b = int(min(255.0, max(0.0, ab * inv)) + 0.5)
                a = int(min(255.0, max(0.0, aa)) + 0.5)
            o = (orow + x) * 4
            out[o] = r
            out[o + 1] = g
            out[o + 2] = b
            out[o + 3] = a
    return out


# ---------------------------------------------------------------------- main

def resize_file(src, dst, dw, dh):
    try:
        from PIL import Image
    except ImportError:
        w, h, px = read_rgba(src)
        print("%s: %dx%d -> %dx%d (pure-python Lanczos)" % (
            os.path.basename(src), w, h, dw, dh))
        write_rgba(dst, dw, dh, resize_rgba(w, h, px, dw, dh))
    else:
        img = Image.open(src).convert('RGBA')
        print("%s: %dx%d -> %dx%d (PIL Lanczos)" % (
            os.path.basename(src), img.width, img.height, dw, dh))
        # PIL filters premultiplied via 'RGBa' to avoid edge fringing.
        img.convert('RGBa').resize((dw, dh), Image.LANCZOS) \
           .convert('RGBA').save(dst, 'PNG', optimize=True)


def main(argv):
    if len(argv) != 4:
        print(__doc__.strip())
        return 2
    src, dst, spec = argv[1], argv[2], argv[3]
    if 'x' in spec.lower():
        dw, dh = (int(v) for v in spec.lower().split('x', 1))
    else:
        dw = dh = int(spec)
    if dw < 1 or dh < 1:
        print("error: target size must be positive")
        return 2
    resize_file(src, dst, dw, dh)
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv))
