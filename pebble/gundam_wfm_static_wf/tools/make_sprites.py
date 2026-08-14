#!/usr/bin/env python3
"""
Sprite generator for the Gundam SD watchface
============================================

Draws the Sazabi, Zeta Gundam and Crossbone Gundam X-1 Full Cloth suit
sprites and writes them into resources/images/, plus the ``~chalk`` variants
the round watch needs.

Usage
-----
  python make_sprites.py            # write all suits into resources/images
  python make_sprites.py sazabi     # just one
  python make_sprites.py --outdir /tmp/preview

Sizing contract (keep in sync with src/c/main.c)
------------------------------------------------
main.c anchors the time/date/steps stack to the bottom of the screen and
gives the sprite band whatever is left above it, minus a reserved top bezel.
That works out to:

  emery (200 x 228):  band 200 wide x 130 tall
  chalk (180 x 180):  band 180 wide x 100 tall

A bitmap taller than its band is silently clipped by the BitmapLayer -- the
suit would lose its head into the bezel -- so every sprite is generated at or
under EMERY_MAX_H, and the round build gets a second rasterization at
CHALK_SCALE rather than a resample of the emery bitmap. The existing
hand-drawn suits follow the same size rule (their ``~chalk`` variants are all
100 tall or less), emit() refuses to write anything that overflows, and
main.c's [BEZEL] log line is the runtime backstop if either number drifts.

Trim the drawing, never the bezel in main.c -- the empty top margin is the
fixed part of that layout.
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from resize_sprite import write_rgba                       # noqa: E402
from spritelib import (Canvas, beam, detail, ellipse, part,  # noqa: E402
                       rect, rgba, sym, symd, thick_line)

# Band sizes from src/c/main.c. Sprites are kept a couple of pixels under so
# a font-metric change on either platform cannot instantly overflow them.
EMERY_MAX_H = 130
EMERY_MAX_W = 200
CHALK_MAX_H = 100
CHALK_MAX_W = 160

# Chalk redraw scale. The drawing is 128 units tall, so 0.76 lands at
# 97px -- inside the 100px round band with a pixel to spare.
CHALK_SCALE = 0.76

# -----------------------------------------------------------------------------
# Shared chibi proportions
# -----------------------------------------------------------------------------
# All three suits are built on the same skeleton so they read as one set when
# the wearer flips between them in the config, and so they all stand on the
# same baseline under the bottom-aligned BitmapLayer. Distinguishing gear
# (funnels, wing binders, cloak, weapons) hangs off the sides; only that gear
# changes the final cropped width.
# The silhouette is a pyramid on purpose: a head wider than the torso,
# pauldrons wider than the head, then a clear gap between the legs. That
# stack is what keeps the suits readable once they are quantized to the
# watch palette and scaled to 98px for the round display.
CX = 76.0        # body centre line
BOT = 126.0      # sole of the feet == bottom of the drawing

HEAD_TOP, HEAD_BOT = 12.0, 58.0
HEAD_HW = 24.0                    # head half-width
TORSO_TOP, TORSO_BOT = 56.0, 86.0
TORSO_HW = 16.0
SH_TOP, SH_BOT = 54.0, 82.0       # shoulder pauldrons
SH_OUT = 40.0                     # outermost pauldron edge from CX
ARM_IN, ARM_OUT = 22.0, 35.0
ARM_TOP = 74.0                    # tucked under the pauldron, which is drawn
                                  # after the arm so only the lower arm shows
SKIRT_TOP, SKIRT_BOT = 82.0, 98.0
LEG_TOP = 94.0
LEG_IN, LEG_OUT = 4.0, 20.0       # inner/outer edge of one leg from CX


def head_shell(top=HEAD_TOP, bot=HEAD_BOT, hw=HEAD_HW):
    """Rounded, chin-tapered helmet outline."""
    return [(CX - hw + 4, top), (CX + hw - 4, top),
            (CX + hw, top + 8), (CX + hw - 1, bot - 13),
            (CX + hw - 9, bot), (CX - hw + 9, bot),
            (CX - hw + 1, bot - 13), (CX - hw, top + 8)]


def torso_shell():
    return [(CX - TORSO_HW, TORSO_TOP), (CX + TORSO_HW, TORSO_TOP),
            (CX + TORSO_HW + 2, TORSO_TOP + 11),
            (CX + TORSO_HW - 1, TORSO_BOT),
            (CX - TORSO_HW + 1, TORSO_BOT),
            (CX - TORSO_HW - 2, TORSO_TOP + 11)]


def pauldron(out=SH_OUT, top=SH_TOP, bot=SH_BOT):
    """Right-hand pauldron; pass through sym() to get the pair."""
    return [(CX + TORSO_HW, top + 1), (CX + out - 12, top - 3),
            (CX + out, top + 11), (CX + out - 2, bot - 6),
            (CX + out - 17, bot), (CX + TORSO_HW, bot - 8)]


def arm_upper():
    return rect(CX + ARM_IN, ARM_TOP, ARM_OUT - ARM_IN, 16)


def arm_fore():
    return rect(CX + ARM_IN + 1, ARM_TOP + 15, ARM_OUT - ARM_IN - 2, 16)


def arm_hand():
    return rect(CX + ARM_IN + 2, ARM_TOP + 30, ARM_OUT - ARM_IN - 4, 8)


def leg_thigh():
    return rect(CX + LEG_IN, LEG_TOP, LEG_OUT - LEG_IN, 14)


def leg_knee():
    return [(CX + LEG_IN, LEG_TOP + 12), (CX + LEG_OUT, LEG_TOP + 12),
            (CX + LEG_OUT - 1, LEG_TOP + 20), (CX + LEG_IN + 1, LEG_TOP + 20)]


def leg_shin():
    return rect(CX + LEG_IN + 1, LEG_TOP + 18, LEG_OUT - LEG_IN - 2, 9)


def leg_foot():
    return [(CX + LEG_IN - 2, BOT - 8), (CX + LEG_OUT, BOT - 8),
            (CX + LEG_OUT + 2, BOT), (CX + LEG_IN - 3, BOT)]


def side_skirt():
    return [(CX + TORSO_HW - 10, SKIRT_TOP - 2), (CX + TORSO_HW + 1, SKIRT_TOP),
            (CX + TORSO_HW + 3, SKIRT_BOT + 3), (CX + TORSO_HW - 12, SKIRT_BOT)]


def front_skirt():
    return [(CX - 12, SKIRT_TOP), (CX + 12, SKIRT_TOP),
            (CX + 10, SKIRT_BOT + 1), (CX - 10, SKIRT_BOT + 1)]


# =============================================================================
# Sazabi (MSN-04) -- Char's crimson heavy suit: mono-eye, huge shoulders,
# funnels trailing off to one side, beam tomahawk raised.
# =============================================================================
def draw_sazabi(scale=1.0):
    OUT = rgba('#280a12')
    RED_HI = rgba('#d94257')
    RED = rgba('#ab1a33')
    RED_D = rgba('#77101f')
    GRAY = rgba('#4c4c58')
    GRAY_D = rgba('#26262f')
    GOLD = rgba('#e8b92e')
    GOLD_D = rgba('#9c7a17')
    EYE = rgba('#7cf2ff')
    BEAM = rgba('#ff48a8')
    GLOW = rgba('#ff9ad4', 80)

    c = Canvas(152, 128, scale)

    # ---- funnels peeling off to the upper left ---------------------------
    # Drawn far-to-near so each pod overlaps the one behind it and the group
    # reads as a receding trail rather than three loose blocks.
    for (fx, fy, s) in ((36, 4, 0.62), (19, 25, 0.80), (1, 49, 1.0)):
        w, h = 19 * s, 13 * s
        # Blunt wedge nose to the right, thruster bell on the left.
        part(c, [(fx + w * 0.30, fy), (fx + w * 0.80, fy),
                 (fx + w, fy + h * 0.34), (fx + w, fy + h * 0.66),
                 (fx + w * 0.80, fy + h), (fx + w * 0.30, fy + h)],
             RED, OUT)
        detail(c, rect(fx + w * 0.34, fy + h * 0.14, w * 0.4, h * 0.26), RED_HI)
        detail(c, rect(fx + w * 0.34, fy + h * 0.62, w * 0.5, h * 0.24), RED_D)
        part(c, [(fx, fy + h * 0.10), (fx + w * 0.30, fy + h * 0.26),
                 (fx + w * 0.30, fy + h * 0.74), (fx, fy + h * 0.90)],
             GRAY_D, OUT)

    # ---- legs (behind the skirt) ------------------------------------------
    sym(c, leg_thigh(), RED, OUT, CX)
    sym(c, leg_knee(), GRAY, OUT, CX)
    sym(c, leg_shin(), RED, OUT, CX)
    sym(c, leg_foot(), RED_D, OUT, CX)
    symd(c, rect(CX + LEG_IN + 2, LEG_TOP + 2, 4, 9), RED_HI, CX)
    symd(c, rect(CX + LEG_IN + 3, LEG_TOP + 21, 11, 3), GRAY_D, CX)

    # ---- arms (behind the pauldrons, which cap them) ----------------------
    sym(c, arm_upper(), RED_D, OUT, CX)
    sym(c, arm_fore(), RED, OUT, CX)
    symd(c, rect(CX + ARM_IN + 2, ARM_TOP + 17, 4, 12), RED_HI, CX)
    symd(c, rect(CX + ARM_IN + 2, ARM_TOP + 15, 11, 2), RED_D, CX)
    sym(c, arm_hand(), GRAY_D, OUT, CX)

    # ---- backpack boosters, just cresting the shoulders ------------------
    sym(c, [(CX + 18, SH_TOP - 13), (CX + 34, SH_TOP - 9),
            (CX + 36, SH_TOP + 8), (CX + 20, SH_TOP + 6)], GRAY, OUT, CX)
    symd(c, [rect(CX + 22, SH_TOP - 8, 11, 4),
             rect(CX + 22, SH_TOP - 2, 11, 4)], GRAY_D, CX)

    # ---- skirt -----------------------------------------------------------
    sym(c, side_skirt(), RED_D, OUT, CX)
    part(c, front_skirt(), RED, OUT)
    # Neo Zeon crest, reduced to a gold glyph -- more line work than this
    # turns to mud once the SDK quantizes to the watch palette.
    detail(c, [[(CX, SKIRT_TOP + 2), (CX + 5, SKIRT_TOP + 10),
                (CX, SKIRT_TOP + 7), (CX - 5, SKIRT_TOP + 10)],
               rect(CX - 7, SKIRT_TOP + 12, 14, 2)], GOLD)

    # ---- torso -----------------------------------------------------------
    part(c, torso_shell(), RED, OUT)
    detail(c, [(CX - TORSO_HW - 1, TORSO_TOP + 3), (CX - TORSO_HW + 6, TORSO_TOP + 2),
               (CX - TORSO_HW + 5, TORSO_BOT - 1), (CX - TORSO_HW, TORSO_BOT - 1)],
           RED_HI)
    part(c, rect(CX - 9, TORSO_TOP + 8, 18, 15), GRAY_D, GOLD)
    detail(c, rect(CX - 7, TORSO_TOP + 10, 14, 3), GRAY)
    symd(c, [rect(CX + 11, TORSO_TOP + 10, 5, 4),
             rect(CX + 11, TORSO_TOP + 17, 5, 4)], GOLD_D, CX)

    # ---- shoulders -------------------------------------------------------
    sym(c, pauldron(), RED, OUT, CX)
    symd(c, [(CX + TORSO_HW + 2, SH_TOP + 1), (CX + SH_OUT - 13, SH_TOP - 2),
             (CX + SH_OUT - 5, SH_TOP + 8), (CX + TORSO_HW + 2, SH_TOP + 8)],
         RED_HI, CX)
    symd(c, [rect(CX + 23, SH_TOP + 11, 14, 4),
             rect(CX + 23, SH_TOP + 17, 14, 4),
             rect(CX + 22, SH_TOP + 23, 13, 3)], GRAY_D, CX)
    symd(c, rect(CX + 21, SH_TOP + 2, 15, 2), GOLD_D, CX)

    # ---- head ------------------------------------------------------------
    # Gold collar, drawn before the helmet so the chin sits on top of it.
    part(c, [(CX - 19, HEAD_BOT - 8), (CX + 19, HEAD_BOT - 8),
             (CX + 21, TORSO_TOP + 5), (CX - 21, TORSO_TOP + 5)], GOLD, OUT)
    detail(c, rect(CX - 17, TORSO_TOP + 1, 34, 3), GOLD_D)
    # The tall forehead blade is the strongest Sazabi read at this size, so
    # it gets every pixel above the helmet.
    part(c, [(CX - 5, HEAD_TOP + 5), (CX + 2, 0), (CX + 7, 0),
             (CX + 9, HEAD_TOP + 5)], RED, OUT)
    detail(c, [(CX - 1, HEAD_TOP + 3), (CX + 3, 3), (CX + 5, 3),
               (CX + 5, HEAD_TOP + 3)], RED_HI)

    # Zeon-style ear vents, poking out past the helmet on both sides.
    sym(c, [(CX + HEAD_HW - 4, HEAD_TOP + 16), (CX + HEAD_HW + 4, HEAD_TOP + 18),
            (CX + HEAD_HW + 4, HEAD_TOP + 28), (CX + HEAD_HW - 4, HEAD_TOP + 30)],
        GRAY, OUT, CX)
    symd(c, [rect(CX + HEAD_HW - 2, HEAD_TOP + 20, 5, 2),
             rect(CX + HEAD_HW - 2, HEAD_TOP + 24, 5, 2)], GRAY_D, CX)

    part(c, head_shell(), RED, OUT)
    detail(c, [(CX - HEAD_HW + 3, HEAD_TOP + 2), (CX - HEAD_HW + 10, HEAD_TOP + 1),
               (CX - HEAD_HW + 9, HEAD_BOT - 2), (CX - HEAD_HW + 3, HEAD_BOT - 6)],
           RED_HI)
    # Mono-eye: dark sensor slot, eye tracked off centre.
    part(c, [(CX - HEAD_HW + 4, HEAD_TOP + 15), (CX + HEAD_HW - 4, HEAD_TOP + 15),
             (CX + HEAD_HW - 6, HEAD_TOP + 26), (CX - HEAD_HW + 6, HEAD_TOP + 26)],
         GRAY_D, OUT)
    detail(c, ellipse(CX + 8, HEAD_TOP + 20, 5.4, 3.6), EYE)
    detail(c, ellipse(CX + 8, HEAD_TOP + 20, 2.2, 1.8), rgba('#ffffff'))
    # Cheek trim and the chin block.
    symd(c, rect(CX + 11, HEAD_TOP + 29, 7, 5), GOLD_D, CX)
    part(c, [(CX - 10, HEAD_BOT - 7), (CX + 10, HEAD_BOT - 7),
             (CX + 8, HEAD_BOT), (CX - 8, HEAD_BOT)], RED_D, OUT)
    detail(c, rect(CX - 6, HEAD_BOT - 5, 12, 2), GRAY_D)

    # ---- beam tomahawk ---------------------------------------------------
    part(c, thick_line(CX + 30, ARM_TOP + 32, CX + 42, ARM_TOP + 10, 6),
         GRAY, OUT)
    beam(c, CX + 43, ARM_TOP + 7, CX + 58, HEAD_TOP - 2, 5.0, BEAM, GLOW)

    return c


# =============================================================================
# Zeta Gundam (MSZ-006) -- white/blue with the gold V-fin, red flying-armor
# binders swept back, hyper mega launcher held across the body.
# =============================================================================
def draw_zeta(scale=1.0):
    OUT = rgba('#15161d')
    WHITE = rgba('#f0f2f6')
    WHITE_D = rgba('#c2c6d2')
    BLUE = rgba('#28407f')
    BLUE_D = rgba('#182a5c')
    RED = rgba('#bc2f2c')
    RED_D = rgba('#84201d')
    GOLD = rgba('#e8c24a')
    EYE = rgba('#3fe8c8')
    GUN = rgba('#3a3e48')
    GUN_D = rgba('#22252c')
    ORANGE = rgba('#e08a2a')

    c = Canvas(160, 128, scale)

    # ---- flying-armor binders, swept up and out ---------------------------
    # One tapering blade per side, rooted at the backpack so it reads as
    # attached rather than as a red slab parked behind the shoulder.
    sym(c, [(CX + 12, SH_TOP + 10), (CX + 22, SH_TOP - 4),
            (CX + 48, HEAD_TOP + 2), (CX + 52, HEAD_TOP + 12),
            (CX + 30, SH_TOP + 16)], RED, OUT, CX)
    symd(c, [(CX + 20, SH_TOP + 2), (CX + 44, HEAD_TOP + 6),
             (CX + 46, HEAD_TOP + 11), (CX + 26, SH_TOP + 10)], RED_D, CX)
    symd(c, [(CX + 44, HEAD_TOP + 4), (CX + 51, HEAD_TOP + 10),
             (CX + 48, HEAD_TOP + 13), (CX + 41, HEAD_TOP + 8)], WHITE_D, CX)

    # ---- backpack thrusters ----------------------------------------------
    sym(c, rect(CX + 14, SH_TOP - 8, 15, 20), GUN, OUT, CX)
    symd(c, rect(CX + 17, SH_TOP - 5, 9, 5), GUN_D, CX)

    # ---- legs -------------------------------------------------------------
    sym(c, leg_thigh(), WHITE, OUT, CX)
    sym(c, leg_knee(), BLUE, OUT, CX)
    sym(c, leg_shin(), WHITE_D, OUT, CX)
    sym(c, leg_foot(), RED, OUT, CX)
    symd(c, rect(CX + LEG_IN + 2, LEG_TOP + 2, 4, 9), rgba('#ffffff'), CX)

    # ---- arms -------------------------------------------------------------
    sym(c, arm_upper(), BLUE_D, OUT, CX)
    sym(c, arm_fore(), WHITE, OUT, CX)
    symd(c, rect(CX + ARM_IN + 2, ARM_TOP + 17, 4, 12), rgba('#ffffff'), CX)
    symd(c, rect(CX + ARM_IN + 2, ARM_TOP + 15, 11, 2), BLUE, CX)
    sym(c, arm_hand(), WHITE_D, OUT, CX)

    # ---- skirt ------------------------------------------------------------
    sym(c, side_skirt(), WHITE, OUT, CX)
    part(c, front_skirt(), RED, OUT)
    detail(c, rect(CX - 5, SKIRT_TOP + 3, 10, 10), GOLD)

    # ---- torso ------------------------------------------------------------
    part(c, torso_shell(), BLUE, OUT)
    detail(c, [(CX - TORSO_HW - 1, TORSO_TOP + 3), (CX - TORSO_HW + 6, TORSO_TOP + 2),
               (CX - TORSO_HW + 5, TORSO_BOT - 1), (CX - TORSO_HW, TORSO_BOT - 1)],
           rgba('#33509c'))
    # Yellow chest intakes: after the V-fin, the fastest Zeta read there is.
    sym(c, rect(CX + 4, TORSO_TOP + 5, 11, 12), GOLD, OUT, CX)
    symd(c, rect(CX + 6, TORSO_TOP + 7, 7, 3), rgba('#f6dc86'), CX)
    part(c, rect(CX - 6, TORSO_TOP + 19, 12, 9), RED_D, OUT)
    symd(c, rect(CX + 8, TORSO_TOP + 20, 7, 6), WHITE_D, CX)

    # ---- shoulders --------------------------------------------------------
    sym(c, pauldron(), WHITE, OUT, CX)
    symd(c, [(CX + TORSO_HW + 2, SH_TOP + 1), (CX + SH_OUT - 13, SH_TOP - 2),
             (CX + SH_OUT - 5, SH_TOP + 8), (CX + TORSO_HW + 2, SH_TOP + 8)],
         rgba('#ffffff'), CX)
    symd(c, [rect(CX + 21, SH_TOP + 17, 17, 5),
             rect(CX + 22, SH_TOP + 24, 14, 3)], BLUE, CX)
    symd(c, rect(CX + 20, SH_TOP + 11, 18, 3), RED, CX)
    symd(c, [(CX + TORSO_HW + 1, SH_TOP + 2), (CX + SH_OUT - 13, SH_TOP - 1),
             (CX + SH_OUT - 10, SH_TOP + 2), (CX + TORSO_HW + 1, SH_TOP + 5)],
         BLUE_D, CX)

    # ---- head -------------------------------------------------------------
    # V-fin: two gold blades swept up and out, red crest between them.
    sym(c, [(CX + 5, HEAD_TOP + 12), (CX + 34, HEAD_TOP - 10),
            (CX + 37, HEAD_TOP - 4), (CX + 10, HEAD_TOP + 18)], GOLD, OUT, CX)
    part(c, [(CX - 5, HEAD_TOP - 4), (CX + 5, HEAD_TOP - 4),
             (CX + 6, HEAD_TOP + 10), (CX - 6, HEAD_TOP + 10)], RED, OUT)
    detail(c, rect(CX - 3, HEAD_TOP - 2, 3, 10), rgba('#d9564f'))

    part(c, head_shell(), WHITE, OUT)
    detail(c, [(CX - HEAD_HW + 3, HEAD_TOP + 2), (CX - HEAD_HW + 10, HEAD_TOP + 1),
               (CX - HEAD_HW + 9, HEAD_BOT - 2), (CX - HEAD_HW + 3, HEAD_BOT - 6)],
           WHITE_D)
    # Twin eyes in a dark face mask.
    part(c, [(CX - HEAD_HW + 3, HEAD_TOP + 15), (CX + HEAD_HW - 3, HEAD_TOP + 15),
             (CX + HEAD_HW - 5, HEAD_TOP + 27), (CX - HEAD_HW + 5, HEAD_TOP + 27)],
         GUN_D, OUT)
    symd(c, [(CX + 4, HEAD_TOP + 17), (CX + 15, HEAD_TOP + 17),
             (CX + 14, HEAD_TOP + 25), (CX + 4, HEAD_TOP + 25)], EYE, CX)
    detail(c, rect(CX - 2, HEAD_TOP + 16, 4, 11), WHITE_D)
    # Red chin vent with its slats.
    part(c, [(CX - 11, HEAD_TOP + 29), (CX + 11, HEAD_TOP + 29),
             (CX + 8, HEAD_BOT), (CX - 8, HEAD_BOT)], RED, OUT)
    detail(c, [rect(CX - 8, HEAD_TOP + 31, 16, 2),
               rect(CX - 7, HEAD_TOP + 35, 14, 2)], RED_D)

    # ---- hyper mega launcher, carried across the body ---------------------
    part(c, thick_line(6, 100, CX + 34, 78, 12), GUN, OUT)
    detail(c, thick_line(12, 99, CX + 30, 78, 4), GUN_D)
    part(c, thick_line(2, 101, 14, 100, 18), GUN_D, OUT)
    detail(c, thick_line(26, 97, 38, 96, 14), ORANGE)
    part(c, thick_line(CX - 20, 92, CX - 16, 102, 7), GUN_D, OUT)
    detail(c, ellipse(4, 101, 2.6, 4.4), EYE)

    return c


# =============================================================================
# Crossbone Gundam X-1 Full Cloth -- skull-and-crossbones cloak over the
# cream/navy frame, X antenna, beam zanber raised.
# =============================================================================
def draw_crossbone(scale=1.0):
    OUT = rgba('#12121a')
    CREAM = rgba('#ece5d1')
    CREAM_D = rgba('#bfb599')
    WHITE = rgba('#f6f7fa')
    NAVY = rgba('#3d3f63')
    NAVY_D = rgba('#252740')
    RED = rgba('#b1332a')
    GOLD = rgba('#d9a63c')
    EYE = rgba('#66e6ff')
    BONE = rgba('#f2f2f2')
    STEEL = rgba('#4c4f5a')
    BEAM = rgba('#ff66c4')
    GLOW = rgba('#ffb0e0', 90)

    c = Canvas(158, 128, scale)

    # ---- cloak, hanging behind everything ---------------------------------
    # Ragged hem: the Full Cloth's cape is the widest thing in the
    # silhouette, so it is what has to stay inside the sprite band.
    sym(c, [(CX + 6, SH_TOP - 6), (CX + 34, SH_TOP + 2), (CX + 46, SH_TOP + 34),
            (CX + 45, BOT - 4), (CX + 38, BOT - 10), (CX + 33, BOT - 2),
            (CX + 27, BOT - 12), (CX + 20, BOT - 4), (CX + 12, BOT - 14),
            (CX + 4, SKIRT_TOP)], NAVY, OUT, CX)
    symd(c, [(CX + 12, SH_TOP), (CX + 30, SH_TOP + 6), (CX + 40, SH_TOP + 34),
             (CX + 39, BOT - 12), (CX + 20, BOT - 16), (CX + 12, SKIRT_TOP)],
         NAVY_D, CX)

    # ---- core fighter binders cresting the shoulders ----------------------
    sym(c, [(CX + 14, SH_TOP - 14), (CX + 34, SH_TOP - 18),
            (CX + 38, SH_TOP - 2), (CX + 18, SH_TOP + 2)], WHITE, OUT, CX)
    symd(c, rect(CX + 20, SH_TOP - 13, 12, 4), CREAM_D, CX)

    # ---- legs -------------------------------------------------------------
    sym(c, leg_thigh(), CREAM, OUT, CX)
    sym(c, leg_knee(), NAVY, OUT, CX)
    sym(c, leg_shin(), STEEL, OUT, CX)
    sym(c, leg_foot(), RED, OUT, CX)
    symd(c, rect(CX + LEG_IN + 2, LEG_TOP + 2, 4, 9), WHITE, CX)

    # ---- arms -------------------------------------------------------------
    sym(c, arm_upper(), NAVY_D, OUT, CX)
    sym(c, arm_fore(), CREAM, OUT, CX)
    symd(c, rect(CX + ARM_IN + 2, ARM_TOP + 17, 4, 12), WHITE, CX)
    symd(c, rect(CX + ARM_IN + 2, ARM_TOP + 15, 11, 2), NAVY, CX)
    sym(c, arm_hand(), WHITE, OUT, CX)

    # ---- skirt ------------------------------------------------------------
    sym(c, side_skirt(), CREAM, OUT, CX)
    part(c, front_skirt(), NAVY, OUT)
    detail(c, rect(CX - 7, SKIRT_TOP + 3, 14, 3), GOLD)

    # ---- muramasa blaster tubes across the waist (the "Full Cloth" bit) ----
    for i in range(4):
        tx = CX - 22 + i * 12
        part(c, rect(tx, SKIRT_TOP - 2, 10, 9), WHITE, OUT)
        detail(c, rect(tx + 1, SKIRT_TOP, 8, 3), GOLD)
        detail(c, rect(tx + 7, SKIRT_TOP + 4, 3, 3), STEEL)

    # ---- torso ------------------------------------------------------------
    part(c, torso_shell(), CREAM, OUT)
    detail(c, [(CX - TORSO_HW - 1, TORSO_TOP + 3), (CX - TORSO_HW + 6, TORSO_TOP + 2),
               (CX - TORSO_HW + 5, TORSO_BOT - 1), (CX - TORSO_HW, TORSO_BOT - 1)],
           CREAM_D)
    symd(c, rect(CX + 9, TORSO_TOP + 4, 7, 16), RED, CX)
    part(c, [(CX - 13, TORSO_TOP - 3), (CX + 13, TORSO_TOP - 3),
             (CX + 14, TORSO_TOP + 3), (CX - 14, TORSO_TOP + 3)], NAVY, OUT)

    # ---- shoulders --------------------------------------------------------
    sym(c, pauldron(), NAVY, OUT, CX)
    symd(c, [(CX + TORSO_HW + 2, SH_TOP + 1), (CX + SH_OUT - 13, SH_TOP - 2),
             (CX + SH_OUT - 5, SH_TOP + 8), (CX + TORSO_HW + 2, SH_TOP + 8)],
         rgba('#4e5079'), CX)
    symd(c, rect(CX + 21, SH_TOP + 22, 16, 3), GOLD, CX)

    # Skull-and-crossbones: on the chest, and again on one pauldron.
    _skull(c, CX, TORSO_TOP + 14, 1.0, BONE, OUT)
    _skull(c, CX + 29, SH_TOP + 13, 0.7, BONE, OUT)

    # ---- head -------------------------------------------------------------
    # X-shaped antenna -- the suit's whole identity in two crossed blades.
    # The blades cross just above the helmet crown so the whole X clears the
    # head; crossing any lower and the lower half hides behind the helmet,
    # leaving what reads as a pair of horns.
    part(c, [thick_line(CX - 25, 0, CX + 20, HEAD_TOP + 10, 4),
             thick_line(CX + 25, 0, CX - 20, HEAD_TOP + 10, 4)],
         GOLD, OUT)

    part(c, head_shell(), WHITE, OUT)
    detail(c, [(CX - HEAD_HW + 3, HEAD_TOP + 2), (CX - HEAD_HW + 10, HEAD_TOP + 1),
               (CX - HEAD_HW + 9, HEAD_BOT - 2), (CX - HEAD_HW + 3, HEAD_BOT - 6)],
           CREAM_D)
    # Single wide visor.
    part(c, [(CX - HEAD_HW + 3, HEAD_TOP + 15), (CX + HEAD_HW - 3, HEAD_TOP + 15),
             (CX + HEAD_HW - 5, HEAD_TOP + 26), (CX - HEAD_HW + 5, HEAD_TOP + 26)],
         NAVY_D, OUT)
    detail(c, [(CX - HEAD_HW + 5, HEAD_TOP + 17), (CX + HEAD_HW - 5, HEAD_TOP + 17),
               (CX + HEAD_HW - 7, HEAD_TOP + 24), (CX - HEAD_HW + 7, HEAD_TOP + 24)],
           EYE)
    detail(c, rect(CX - HEAD_HW + 6, HEAD_TOP + 18, 8, 2), rgba('#d8f8ff'))
    # Gold mouth guard -- kept small; at full chin width it reads as a beard.
    part(c, [(CX - 8, HEAD_TOP + 29), (CX + 8, HEAD_TOP + 29),
             (CX + 6, HEAD_BOT - 2), (CX - 6, HEAD_BOT - 2)], GOLD, OUT)
    detail(c, rect(CX - 5, HEAD_TOP + 32, 10, 2), rgba('#a67d24'))

    # ---- beam zanber, raised ---------------------------------------------
    part(c, thick_line(CX + 30, ARM_TOP + 32, CX + 44, ARM_TOP + 8, 7),
         STEEL, OUT)
    beam(c, CX + 45, ARM_TOP + 5, CX + 62, HEAD_TOP - 6, 6.0, BEAM, GLOW)

    return c


def _skull(c, cx_, cy, s, bone, out):
    """Skull-and-crossbones badge centred on (cx_, cy)."""
    bone_l = thick_line(cx_ - 9 * s, cy + 6 * s, cx_ + 9 * s, cy - 2 * s, 3 * s)
    bone_r = thick_line(cx_ - 9 * s, cy - 2 * s, cx_ + 9 * s, cy + 6 * s, 3 * s)
    part(c, [bone_l, bone_r], bone, out)
    cranium = [(cx_ - 6 * s, cy - 7 * s), (cx_ + 6 * s, cy - 7 * s),
               (cx_ + 7 * s, cy - 1 * s), (cx_ + 4 * s, cy + 4 * s),
               (cx_ - 4 * s, cy + 4 * s), (cx_ - 7 * s, cy - 1 * s)]
    part(c, cranium, bone, out)
    detail(c, [rect(cx_ - 4.5 * s, cy - 4 * s, 3 * s, 3 * s),
               rect(cx_ + 1.5 * s, cy - 4 * s, 3 * s, 3 * s),
               rect(cx_ - 1 * s, cy + 0.5 * s, 2 * s, 2.5 * s)], out)


# =============================================================================
# Output
# =============================================================================
SUITS = {
    'sazabi': draw_sazabi,
    'zeta': draw_zeta,
    'crossbone': draw_crossbone,
}


def _write(name, suffix, canvas, outdir, max_w, max_h, label):
    canvas = canvas.cropped()
    if canvas.h > max_h or canvas.w > max_w:
        raise SystemExit(
            "%s%s: %dx%d exceeds the %s sprite band (%dx%d) -- shrink the "
            "drawing, never the bezel in main.c"
            % (name, suffix, canvas.w, canvas.h, label, max_w, max_h))
    path = os.path.join(outdir, '%s_idle%s.png' % (name, suffix))
    write_rgba(path, canvas.w, canvas.h, canvas.px)
    print("  %-6s %3dx%-3d -> %s" % (label, canvas.w, canvas.h,
                                     os.path.basename(path)))


def emit(name, factory, outdir):
    print(name)
    _write(name, '', factory(1.0), outdir, EMERY_MAX_W, EMERY_MAX_H, 'emery')
    # The round build gets its own rasterization rather than a resample of the
    # emery bitmap -- see the Canvas docstring in spritelib.py for why.
    _write(name, '~chalk', factory(CHALK_SCALE), outdir,
           CHALK_MAX_W, CHALK_MAX_H, 'chalk')


def main(argv):
    here = os.path.dirname(os.path.abspath(__file__))
    outdir = os.path.join(here, '..', 'resources', 'images')
    wanted = []
    i = 1
    while i < len(argv):
        if argv[i] == '--outdir':
            outdir = argv[i + 1]
            i += 2
            continue
        wanted.append(argv[i])
        i += 1
    if not wanted:
        wanted = list(SUITS)

    outdir = os.path.normpath(outdir)
    if not os.path.isdir(outdir):
        os.makedirs(outdir)
    for name in wanted:
        if name not in SUITS:
            raise SystemExit("unknown suit %r (have: %s)"
                             % (name, ', '.join(sorted(SUITS))))
        emit(name, SUITS[name], outdir)
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv))
