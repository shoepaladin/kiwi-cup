#!/usr/bin/env python3
"""
Local layout preview for the Gundam SD watchface (no CloudPebble needed)
=======================================================================

Draws the watchface the way src/c/main.c lays it out, using the PNGs you
point it at, so a sprite change can be judged before it goes anywhere near
CloudPebble or a watch.

Usage
-----
  python preview_layout.py NAME [NAME ...]                 # current resources
  python preview_layout.py ae qubeley --images DIR         # a candidate folder
  python preview_layout.py ae qubeley --compare DIR        # current vs DIR
  python preview_layout.py zeta ae --out sheet.png --scale 3 --guides

Options
-------
  --images DIR    where NAME_idle.png / NAME_idle~chalk.png live
                  (default: ../resources/images)
  --compare DIR   draw two columns per suit: resources/images, then DIR
  --guides        outline the sprite band (red) and the top bezel (yellow)
  --scale N       integer zoom for the output (default 2)
  --out FILE      output PNG (default: preview.png next to where you ran it)

What is and is not simulated
----------------------------
Geometry is exact: the constants below are copied from main_window_load() in
src/c/main.c (emery 200x228 rect; chalk 180x180 round), the sprite is
centred horizontally and bottom-aligned in the band, and the chalk screen is
masked to its circle. It also reproduces the [BEZEL] guard: a bitmap taller
than the band is clipped at the top, as the BitmapLayer would.

NOT simulated: the step arc, Bluetooth alert, battery text, and the real
fonts for the date and steps rows (stand-ins are drawn at the right height).
The time uses the project's BlackOpsOne font. Colours are shown after the
same ARGB2222 snap the watch applies, so an unquantized PNG previews the way
it will actually look.

If a NAME has no ~chalk file the base file is used on chalk, which is what
the SDK does. A folder passed to --images/--compare only needs the suits you
changed; anything missing falls back to resources/images.

Needs Pillow only.
"""

import os
import sys

from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.normpath(os.path.join(HERE, '..', 'resources'))

# (label, width, height, round, top_bezel, bottom_inset, time_h, date_h,
#  steps_h, time_font_file, time_font_px, suffix)
PLATFORMS = {
    'emery': dict(w=200, h=228, round=False, bezel=8, bottom=0, time_h=46,
                  date_h=28, steps_h=20, font='BlackOpsOne-Regular-emery.ttf',
                  px=42, date_px=20, steps_px=14, suffix=''),
    'chalk': dict(w=180, h=180, round=True, bezel=4, bottom=6, time_h=36,
                  date_h=22, steps_h=16, font='BlackOpsOne-Regular-chalk.ttf',
                  px=30, date_px=15, steps_px=11, suffix='~chalk'),
}

BG = (0, 0, 0)
FG = (255, 255, 255)


def snap(v):
    return 0 if v < 43 else 85 if v < 128 else 170 if v < 213 else 255


def quantized(img):
    """ARGB2222, no dithering: what the watch does to an unprepared PNG."""
    img = img.convert('RGBA')
    px = img.load()
    for y in range(img.height):
        for x in range(img.width):
            r, g, b, a = px[x, y]
            px[x, y] = (snap(r), snap(g), snap(b), snap(a))
    return img


def band(p):
    steps_y = p['h'] - p['bottom'] - p['steps_h']
    date_y = steps_y - p['date_h'] + 2
    time_y = date_y - p['time_h'] + 2
    top = p['bezel']
    return steps_y, date_y, time_y, top, time_y - top


def load_sprite(folder, name, p):
    # Candidate folder first, then the project's own resources, so a folder
    # that only holds the suits being changed still previews the rest.
    path = None
    for fold in (folder, os.path.join(RES, 'images')):
        for fn in ('%s_idle%s.png' % (name, p['suffix']),
                   '%s_idle.png' % name):
            if os.path.exists(os.path.join(fold, fn)):
                path = os.path.join(fold, fn)
                break
        if path:
            break
    if not path:
        raise SystemExit('missing sprite for %s' % name)
    return quantized(Image.open(path)), os.path.basename(path)


def font(file, px):
    try:
        return ImageFont.truetype(os.path.join(RES, 'fonts', file), px)
    except OSError:
        return ImageFont.load_default()


def render(folder, name, plat, guides=False):
    p = PLATFORMS[plat]
    img = Image.new('RGBA', (p['w'], p['h']), BG + (255,))
    d = ImageDraw.Draw(img)
    steps_y, date_y, time_y, top, band_h = band(p)

    spr, fname = load_sprite(folder, name, p)
    note = ''
    if spr.height > band_h:
        note = 'CLIPPED %dpx' % (spr.height - band_h)
        spr = spr.crop((0, spr.height - band_h, spr.width, spr.height))
    elif spr.width > p['w']:
        note = 'CLIPPED WIDTH'
        x0 = (spr.width - p['w']) // 2
        spr = spr.crop((x0, 0, x0 + p['w'], spr.height))
    ox = (p['w'] - spr.width) // 2
    oy = top + band_h - spr.height
    img.alpha_composite(spr, (ox, oy))

    inner = 8 if not p['round'] else 24
    tf = font(p['font'], p['px'])
    df = ImageFont.load_default(size=p['date_px'])
    sf = ImageFont.load_default(size=p['steps_px'])
    d.text((p['w'] / 2, time_y + p['time_h'] / 2), '10:42', font=tf, fill=FG,
           anchor='mm')
    d.text((p['w'] / 2, date_y + p['date_h'] / 2), 'MON OCT 12', font=df,
           fill=FG, anchor='mm')
    d.text((inner, steps_y + p['steps_h'] / 2), '6,240', font=sf, fill=FG,
           anchor='lm')
    if guides:
        d.rectangle([0, top, p['w'] - 1, top + band_h - 1], outline=(255, 0, 0))
        d.rectangle([0, 0, p['w'] - 1, top - 1], outline=(255, 255, 0))
    if p['round']:
        mask = Image.new('L', img.size, 0)
        ImageDraw.Draw(mask).ellipse([0, 0, p['w'] - 1, p['h'] - 1], fill=255)
        bgc = Image.new('RGBA', img.size, (24, 24, 24, 255))
        bgc.paste(img, (0, 0), mask)
        img = bgc
    label = '%dx%d %s' % (spr.width, spr.height, note)
    return img, label.strip()


def main(argv):
    names, opts = [], {}
    i = 1
    while i < len(argv):
        a = argv[i]
        if a in ('--images', '--compare', '--out', '--scale'):
            opts[a] = argv[i + 1]
            i += 2
        elif a == '--guides':
            opts[a] = True
            i += 1
        elif a.startswith('--'):
            print(__doc__.strip())
            return 2
        else:
            names.append(a)
            i += 1
    if not names:
        print(__doc__.strip())
        return 2

    base = os.path.join(RES, 'images')
    folders = [(opts.get('--images', base), 'current' if '--images' not in opts
                else os.path.basename(opts['--images'].rstrip('/')))]
    if '--compare' in opts:
        folders = [(base, 'current'),
                   (opts['--compare'],
                    os.path.basename(opts['--compare'].rstrip('/')))]
    scale = int(opts.get('--scale', 2))
    guides = '--guides' in opts

    cells = []                      # rows of (title, image, label)
    for n in names:
        row = []
        for folder, tag in folders:
            for plat in ('emery', 'chalk'):
                im, lab = render(folder, n, plat, guides)
                row.append(('%s %s %s' % (n, plat, tag), im, lab))
        cells.append(row)

    pad, head = 10, 30
    cw = max(c[1].width for r in cells for c in r) * scale
    ch = max(c[1].height for r in cells for c in r) * scale
    cols = len(cells[0])
    sheet = Image.new('RGB', (cols * (cw + pad) + pad,
                              len(cells) * (ch + head + pad) + pad),
                      (60, 60, 70))
    d = ImageDraw.Draw(sheet)
    lf = ImageFont.load_default(size=13)
    for r, row in enumerate(cells):
        for c, (title, im, lab) in enumerate(row):
            x = pad + c * (cw + pad)
            y = pad + r * (ch + head + pad)
            d.text((x, y), '%s  [%s]' % (title, lab), font=lf, fill=FG)
            big = im.resize((im.width * scale, im.height * scale), Image.NEAREST)
            sheet.paste(big.convert('RGB'), (x, y + head - 8))
    out = opts.get('--out', 'preview.png')
    sheet.save(out)
    print('wrote %s  (%dx%d)' % (out, sheet.width, sheet.height))
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv))
