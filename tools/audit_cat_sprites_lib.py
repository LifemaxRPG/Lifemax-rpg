"""Shared loading and colour helpers for tools/audit_cat_sprites.py (plain Python + Pillow + numpy).

Reads the shipped cat art exactly as the native loaders do: sheets are 8-bit grey PNGs whose pixel
value is a palette index (0 clear), palettes.json maps index to colour per coat, anims.json gives
each clip's row and frame count (frames are 72x48; side sheets are 8 columns wide, depth sheets 6).
"""

import json
import os

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
PET = os.path.join(HERE, "..", "android-wrapper", "native", "src", "main", "resources", "pet")

COATS = ["black", "ginger", "gray", "tuxedo"]
SIDE_KINDS = ["stage0", "stage1", "stage2", "stage3", "stage4", "stage5", "adult"]
DEPTH_KINDS = ["dstage0", "dstage1", "dstage2", "dstage3", "dstage4", "dstage5", "depth"]
KINDS = SIDE_KINDS + DEPTH_KINDS

OUTLINE = 10
COAT_LO, COAT_HI = 20, 80  # coat shades: lit ramp 20-50, shade ramp 60-80
LIT = (20, 30, 40, 50)
SHADE = (60, 70, 80)
WHITES = (90, 100, 110)
PINKS = (120, 130, 140)
# eyes, mouth, whiskers and other drawn details, not coat texture
FEATURES = (150, 160, 170, 180, 190, 200, 210)
# resting clips (the native RestingFloor / holdable set) and the one-shot slowblink
STILL_CLIPS = ["idle_sit", "idle_sit_w", "loaf", "loaf_w", "sleep", "sleep_w"]
FW, FH = 72, 48


class Clip:
    def __init__(self, d, depth):
        self.name, self.row, self.frames = d["name"], d["row"], d["frames"]
        self.fps, self.bottom, self.depth = d["fps"], d["bottom"], depth


def load_anims():
    with open(os.path.join(PET, "anims.json")) as f:
        a = json.load(f)
    return [Clip(c, False) for c in a["adult"]], [Clip(c, True) for c in a["depth"]]


def load_palettes():
    with open(os.path.join(PET, "palettes.json")) as f:
        raw = json.load(f)
    return {c: {int(k): tuple(int(v[i : i + 2], 16) for i in (0, 2, 4)) for k, v in p.items()}
            for c, p in raw.items()}


def load_sheet(kind, coat):
    return np.array(Image.open(os.path.join(PET, "sheets", f"{kind}-{coat}.png")))


def clips_for(kind, side, depth):
    return depth if kind in DEPTH_KINDS else side


def frames_of(sheet, clip):
    """The clip's frames as an array [n, 48, 72] of palette indices."""
    return np.stack([sheet[clip.row * FH : (clip.row + 1) * FH, f * FW : (f + 1) * FW]
                     for f in range(clip.frames)])


def lut(palette):
    t = np.zeros((256, 4), np.uint8)
    for k, rgb in palette.items():
        t[k] = (*rgb, 255)
    return t


def lab_l(rgb):
    """CIE L* of an sRGB colour, 0..100."""
    c = np.array(rgb, float) / 255.0
    c = np.where(c <= 0.04045, c / 12.92, ((c + 0.055) / 1.055) ** 2.4)
    y = 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2]
    f = y ** (1 / 3) if y > 216 / 24389 else (24389 / 27 * y + 16) / 116
    return 116 * f - 16


def delta_e(a, b):
    """CIE76 colour distance between two sRGB colours."""
    return float(np.linalg.norm(lab(a) - lab(b)))


def lab(rgb):
    c = np.array(rgb, float) / 255.0
    c = np.where(c <= 0.04045, c / 12.92, ((c + 0.055) / 1.055) ** 2.4)
    m = np.array([[0.4124, 0.3576, 0.1805], [0.2126, 0.7152, 0.0722], [0.0193, 0.1192, 0.9505]])
    xyz = m @ c / np.array([0.95047, 1.0, 1.08883])
    f = np.where(xyz > 216 / 24389, np.cbrt(xyz), (24389 / 27 * xyz + 16) / 116)
    return np.array([116 * f[1] - 16, 500 * (f[0] - f[1]), 200 * (f[1] - f[2])])


def l_table(palette):
    """L* per palette index as a 256 array (0 for undefined)."""
    t = np.zeros(256)
    for k, rgb in palette.items():
        t[k] = lab_l(rgb)
    return t


def neighbours8(f):
    """The 8 shifted copies of a frame (out of frame is 0)."""
    p = np.pad(f, 1)
    h, w = f.shape
    return [p[1 + dy : 1 + dy + h, 1 + dx : 1 + dx + w]
            for dy in (-1, 0, 1) for dx in (-1, 0, 1) if (dy, dx) != (0, 0)]


def neighbours4(f):
    p = np.pad(f, 1)
    h, w = f.shape
    return [p[0:h, 1 : w + 1], p[2 : h + 2, 1 : w + 1], p[1 : h + 1, 0:w], p[1 : h + 1, 2 : w + 2]]


def is_coat(a):
    return (a >= COAT_LO) & (a <= COAT_HI)
