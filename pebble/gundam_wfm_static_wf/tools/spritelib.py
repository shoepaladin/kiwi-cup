#!/usr/bin/env python3
"""
Tiny pixel-art rasterizer for the Gundam SD watchface sprites
=============================================================

The six original suit sprites were drawn by hand. The three added later
(Sazabi, Zeta, Crossbone Full Cloth) are *generated* from the part lists in
make_sprites.py, so their silhouettes can be re-tuned when the layout in
src/c/main.c changes without redrawing anything by hand.

Everything here is pure stdlib -- same constraint as resize_sprite.py, so the
tools directory keeps working on a bare Python install.

Model
-----
A sprite is a painter's-algorithm stack of *parts*. A part is one or more
polygons that share a single 1px dark outline: the polygons are rasterized
into a mask, the mask is dilated by one pixel, and the ring between the two
is painted with the outline color before the fill goes down. Doing the
outline from the mask (rather than stroking the edges) is what keeps the
silhouette clean where several polygons in one part touch -- no seams appear
along the shared edges, which is exactly how the hand-drawn sprites read.

Coordinates are floating point in pixel space; a pixel is covered when its
*center* (x+0.5, y+0.5) falls inside the polygon. So rect(x, y, w, h) covers
columns x .. x+w-1 inclusive, which is what you would expect.
"""

import math

# --------------------------------------------------------------------- color


def rgba(hex_str, alpha=255):
    """'#rrggbb' -> (r, g, b, a) tuple."""
    s = hex_str.lstrip('#')
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), alpha)


# -------------------------------------------------------------------- canvas


class Canvas(object):
    """A drawing surface with an optional uniform scale.

    Every part is scaled at rasterization time rather than the finished image
    being resampled afterwards. That matters for the round watch: a Lanczos
    downscale of flat pixel art introduces thousands of intermediate colors
    that dither badly once the SDK quantizes to the watch palette, and it
    smears the 1px outlines. Re-rasterizing keeps the fills flat and the
    outlines exactly one device pixel wide at any size.
    """

    def __init__(self, w, h, scale=1.0):
        self.w = int(round(w * scale))
        self.h = int(round(h * scale))
        self.scale = float(scale)
        self.px = bytearray(self.w * self.h * 4)

    def blend(self, x, y, color):
        """Source-over one pixel. Straight (non-premultiplied) alpha."""
        if x < 0 or y < 0 or x >= self.w or y >= self.h:
            return
        sa = color[3]
        if sa == 0:
            return
        o = (y * self.w + x) * 4
        if sa == 255:
            self.px[o] = color[0]
            self.px[o + 1] = color[1]
            self.px[o + 2] = color[2]
            self.px[o + 3] = 255
            return
        da = self.px[o + 3]
        na = sa + da * (255 - sa) // 255
        if na == 0:
            self.px[o + 3] = 0
            return
        for i in range(3):
            src = color[i] * sa
            dst = self.px[o + i] * da * (255 - sa) // 255
            self.px[o + i] = min(255, (src + dst) // na)
        self.px[o + 3] = na

    def bbox(self):
        """Tight bounding box of non-transparent pixels, or None if empty."""
        x0, y0, x1, y1 = self.w, self.h, -1, -1
        for y in range(self.h):
            row = y * self.w
            for x in range(self.w):
                if self.px[(row + x) * 4 + 3]:
                    if x < x0:
                        x0 = x
                    if x > x1:
                        x1 = x
                    if y < y0:
                        y0 = y
                    if y > y1:
                        y1 = y
        if x1 < 0:
            return None
        return (x0, y0, x1, y1)

    def cropped(self):
        """New Canvas trimmed to bbox. Sprites ship with zero padding so the
        bottom-aligned BitmapLayer in main.c stands every suit on the same
        baseline."""
        box = self.bbox()
        if box is None:
            return Canvas(1, 1)
        x0, y0, x1, y1 = box
        out = Canvas(x1 - x0 + 1, y1 - y0 + 1)
        for y in range(out.h):
            src = ((y + y0) * self.w + x0) * 4
            dst = y * out.w * 4
            out.px[dst:dst + out.w * 4] = self.px[src:src + out.w * 4]
        return out


# ------------------------------------------------------------------ polygons


def _norm(poly_or_polys):
    """Accept a single polygon or a list of polygons; always return a list."""
    if not poly_or_polys:
        return []
    first = poly_or_polys[0]
    if isinstance(first, (tuple, list)) and len(first) == 2 \
            and isinstance(first[0], (int, float)):
        return [poly_or_polys]
    return list(poly_or_polys)


def poly_mask(w, h, polys):
    """Even-odd scanline rasterization of one or more polygons into a mask."""
    mask = bytearray(w * h)
    for pts in polys:
        n = len(pts)
        if n < 3:
            continue
        ys = [p[1] for p in pts]
        y0 = max(0, int(math.floor(min(ys))))
        y1 = min(h - 1, int(math.ceil(max(ys))))
        for y in range(y0, y1 + 1):
            yc = y + 0.5
            xs = []
            for i in range(n):
                ax, ay = pts[i]
                bx, by = pts[(i + 1) % n]
                if (ay <= yc < by) or (by <= yc < ay):
                    xs.append(ax + (yc - ay) * (bx - ax) / (by - ay))
            if not xs:
                continue
            xs.sort()
            row = y * w
            for i in range(0, len(xs) - 1, 2):
                xa = int(math.ceil(xs[i] - 0.5))
                xb = int(math.floor(xs[i + 1] - 0.5))
                if xb < 0 or xa >= w:
                    continue
                for x in range(max(0, xa), min(w - 1, xb) + 1):
                    mask[row + x] = 1
    return mask


def _ring(mask, w, h):
    """8-neighbour dilation of mask, minus mask -- i.e. the 1px outline."""
    ring = bytearray(w * h)
    for y in range(h):
        row = y * w
        for x in range(w):
            if mask[row + x]:
                continue
            hit = False
            for dy in (-1, 0, 1):
                yy = y + dy
                if yy < 0 or yy >= h:
                    continue
                base = yy * w
                for dx in (-1, 0, 1):
                    xx = x + dx
                    if 0 <= xx < w and mask[base + xx]:
                        hit = True
                        break
                if hit:
                    break
            if hit:
                ring[row + x] = 1
    return ring


# ------------------------------------------------------------------ painting


def part(canvas, polys, fill, outline=None):
    """Paint one part: outline ring first, then the fill on top."""
    polys = _norm(polys)
    if not polys:
        return
    s = canvas.scale
    if s != 1.0:
        polys = [[(x * s, y * s) for (x, y) in p] for p in polys]
    mask = poly_mask(canvas.w, canvas.h, polys)
    if outline is not None:
        ring = _ring(mask, canvas.w, canvas.h)
        for i, on in enumerate(ring):
            if on:
                canvas.blend(i % canvas.w, i // canvas.w, outline)
    if fill is not None:
        for i, on in enumerate(mask):
            if on:
                canvas.blend(i % canvas.w, i // canvas.w, fill)


def detail(canvas, polys, fill):
    """Paint polygons with no outline -- panel lines, shading, vents, glow."""
    part(canvas, polys, fill, None)


def rect(x, y, w, h):
    """Axis-aligned rectangle covering columns x..x+w-1, rows y..y+h-1."""
    return [(x, y), (x + w, y), (x + w, y + h), (x, y + h)]


def ellipse(cx, cy, rx, ry, steps=32):
    return [(cx + rx * math.cos(2 * math.pi * i / steps),
             cy + ry * math.sin(2 * math.pi * i / steps))
            for i in range(steps)]


def mirror(pts, axis):
    """Reflect a polygon across the vertical line x = axis."""
    return [(2.0 * axis - x, y) for (x, y) in reversed(pts)]


def mirror_all(polys, axis):
    return [mirror(p, axis) for p in _norm(polys)]


def sym(canvas, polys, fill, outline, axis):
    """Draw polygons together with their mirror image as ONE part, so the
    outline ring is computed over the pair. Every symmetric piece of a suit
    goes through here -- it is the difference between a body that reads as one
    machine and one that reads as two halves glued together."""
    polys = _norm(polys)
    part(canvas, polys + mirror_all(polys, axis), fill, outline)


def symd(canvas, polys, fill, axis):
    """sym() without an outline -- shading, panel lines, vents."""
    polys = _norm(polys)
    detail(canvas, polys + mirror_all(polys, axis), fill)


def thick_line(x0, y0, x1, y1, width):
    """A line segment as a quad, so it can be drawn as an ordinary part."""
    dx, dy = x1 - x0, y1 - y0
    length = math.hypot(dx, dy)
    if length == 0:
        return rect(x0, y0, width, width)
    nx, ny = -dy / length * width / 2.0, dx / length * width / 2.0
    return [(x0 + nx, y0 + ny), (x1 + nx, y1 + ny),
            (x1 - nx, y1 - ny), (x0 - nx, y0 - ny)]


def beam(canvas, x0, y0, x1, y1, width, core, glow):
    """A beam blade: soft wide glow, brighter mid, white-hot core."""
    detail(canvas, thick_line(x0, y0, x1, y1, width * 2.2), glow)
    detail(canvas, thick_line(x0, y0, x1, y1, width * 1.3),
           (glow[0], glow[1], glow[2], min(255, glow[3] * 2)))
    detail(canvas, thick_line(x0, y0, x1, y1, width), core)
    detail(canvas, thick_line(x0, y0, x1, y1, max(1.0, width * 0.34)),
           (255, 255, 255, 235))
