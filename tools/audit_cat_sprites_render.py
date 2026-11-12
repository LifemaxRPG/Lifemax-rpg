"""Contact sheets for tools/audit_cat_sprites.py: every image is the sheet's own pixels at 4x with the
coat's palette applied, on a mid-grey background (a black cat must be visible), cropped to the
sheet kind's drawn area. Heat overlays dim the frame and paint what changed since the previous frame
(the row loops, so frame 0 is compared with the last): magenta = a coat shade (20-80) became another
coat shade, cyan = anything else that changed (eyes, mouth, silhouette)."""

import os

import numpy as np
from PIL import Image, ImageDraw

from audit_cat_sprites_lib import (COATS, DEPTH_KINDS, FH, FW, KINDS, SIDE_KINDS, frames_of,
                                   is_coat, load_sheet, lut)

BG = np.array([138, 146, 160, 255], np.uint8)
LABEL_W = 150
SCALE = 4


def crop_box(kind):
    """x0, x1, y0, y1 of the drawn area of a sheet kind across all four coats."""
    xs, ys = [], []
    for coat in COATS:
        nz = np.argwhere(load_sheet(kind, coat) > 0)
        xs += [(nz[:, 1] % FW).min(), (nz[:, 1] % FW).max()]
        ys += [(nz[:, 0] % FH).min(), (nz[:, 0] % FH).max()]
    return int(min(xs)) - 1, int(max(xs)) + 2, int(min(ys)) - 1, int(max(ys)) + 2


def paint(frame, table, box):
    x0, x1, y0, y1 = box
    f = frame[max(y0, 0):y1, max(x0, 0):x1]
    rgba = table[f]
    return np.where(rgba[..., 3:4] > 0, rgba, BG).astype(np.uint8)


def heat(frame, prev, table, box):
    """The frame dimmed, with the pixels that changed since [prev] painted over."""
    x0, x1, y0, y1 = box
    f, p = frame[max(y0, 0):y1, max(x0, 0):x1], prev[max(y0, 0):y1, max(x0, 0):x1]
    img = paint(frame, table, box).astype(float)
    img = (img * 0.45 + BG * 0.55)
    ch = f != p
    flip = ch & is_coat(f) & is_coat(p)
    other = ch & ~flip
    img[other] = (0, 220, 230, 255)
    img[flip] = (255, 0, 200, 255)
    return img.astype(np.uint8)


def up(img):
    return np.kron(img, np.ones((SCALE, SCALE, 1), np.uint8))


def label_img(text, height, width=LABEL_W):
    im = Image.new("RGBA", (width, height), tuple(BG))
    d = ImageDraw.Draw(im)
    d.text((4, 4), text, fill=(0, 0, 0, 255))
    return np.array(im)


def hstack(cells, gap=4):
    h = max(c.shape[0] for c in cells)
    out = []
    for c in cells:
        if c.shape[0] < h:
            pad = np.tile(BG, (h - c.shape[0], c.shape[1], 1))
            c = np.vstack([c, pad])
        out.append(c)
        out.append(np.tile(BG, (h, gap, 1)))
    return np.hstack(out[:-1])


def vstack(rows, gap=4):
    w = max(r.shape[1] for r in rows)
    out = []
    for r in rows:
        if r.shape[1] < w:
            r = np.hstack([r, np.tile(BG, (r.shape[0], w - r.shape[1], 1))])
        out.append(r)
        out.append(np.tile(BG, (gap, w, 1)))
    return np.vstack(out[:-1])


def save(arr, path):
    Image.fromarray(arr).save(path, optimize=False, compress_level=3)
    return os.path.basename(path)


def clip_row(frames, table, box, heated, label):
    prevs = np.roll(frames, 1, axis=0)
    cells = [up(heat(f, p, table, box) if heated else paint(f, table, box))
             for f, p in zip(frames, prevs)]
    h = cells[0].shape[0]
    return hstack([label_img(label, h)] + cells)


def contact_sheet(coat, kind, clips, palette, out, heated, halves=2):
    """Every clip row of one (coat, kind), frames side by side, in [halves] images."""
    table, box = lut(palette), crop_box(kind)
    sheet = load_sheet(kind, coat)
    names, step = [], max(1, -(-len(clips) // halves))
    for i in range(0, len(clips), step):
        part = clips[i:i + step]
        rows = [clip_row(frames_of(sheet, c), table, box, heated, f"{c.name} r{c.row}")
                for c in part]
        tag = "heat" if heated else "contact"
        suffix = f"_{i // step + 1}" if halves > 1 else ""
        names.append(save(vstack(rows), os.path.join(out, f"{tag}_{coat}_{kind}{suffix}.png")))
    return names


def cross_coat(kind, clips, palettes, out, halves=2, samples=3):
    """Each clip at [samples] frames, the four coats side by side at every sample."""
    box = crop_box(kind)
    tables = {c: lut(palettes[c]) for c in COATS}
    sheets = {c: load_sheet(kind, c) for c in COATS}
    names, step = [], max(1, -(-len(clips) // halves))
    for i in range(0, len(clips), step):
        rows = []
        for c in clips[i:i + step]:
            fr = {k: frames_of(sheets[k], c) for k in COATS}
            picks = sorted({int(round(j * (c.frames - 1) / max(samples - 1, 1)))
                            for j in range(samples)})
            groups = [hstack([up(paint(fr[k][p], tables[k], box)) for k in COATS], gap=2)
                      for p in picks]
            h = groups[0].shape[0]
            rows.append(hstack([label_img(f"{c.name}\n{'/'.join(COATS)}\nframes {picks}", h)]
                               + groups, gap=10))
        suffix = f"_{i // step + 1}" if halves > 1 else ""
        names.append(save(vstack(rows), os.path.join(out, f"crosscoat_{kind}{suffix}.png")))
    return names


def cross_stage(coat, clips, palette, out, halves=2):
    """Each clip's frame 0 and middle frame at every stage 0..5 then adult, side by side."""
    table = lut(palette)
    box = (1, FW - 1, 0, FH)
    sheets = {k: load_sheet(k, coat) for k in SIDE_KINDS}
    names, step = [], max(1, -(-len(clips) // halves))
    for i in range(0, len(clips), step):
        rows = []
        for c in clips[i:i + step]:
            picks = sorted({0, c.frames // 2})
            cells = [up(paint(frames_of(sheets[k], c)[p], table, box))
                     for p in picks for k in SIDE_KINDS]
            h = cells[0].shape[0]
            rows.append(hstack([label_img(f"{c.name}\nstages 0-5,adult\nframes {picks}", h)]
                               + cells, gap=2))
        suffix = f"_{i // step + 1}" if halves > 1 else ""
        names.append(save(vstack(rows), os.path.join(out, f"crossstage_{coat}{suffix}.png")))
    return names


def finding_strip(coat, kind, clip, palette, out, tag):
    """One clip row, plain on top and heat below: the thumbnail of a finding."""
    table, box = lut(palette), crop_box(kind)
    frames = frames_of(load_sheet(kind, coat), clip)
    top = clip_row(frames, table, box, False, f"{coat} {kind}\n{clip.name}")
    bottom = clip_row(frames, table, box, True, "changes vs previous")
    return save(vstack([top, bottom]), os.path.join(out, f"finding_{tag}.png"))


def all_images(side, depth, palettes, out):
    names = []
    for coat in COATS:
        for kind in KINDS:
            clips = depth if kind in DEPTH_KINDS else side
            names += contact_sheet(coat, kind, clips, palettes[coat], out, False,
                                   halves=1 if kind in DEPTH_KINDS else 2)
            names += contact_sheet(coat, kind, clips, palettes[coat], out, True,
                                   halves=1 if kind in DEPTH_KINDS else 2)
        names += cross_stage(coat, side, palettes[coat], out)
    for kind in KINDS:
        names += cross_coat(kind, depth if kind in DEPTH_KINDS else side, palettes, out,
                            halves=1 if kind in DEPTH_KINDS else 2)
    return names
