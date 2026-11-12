#!/usr/bin/env python3
"""Audit every shipped native cat sprite for shading consistency and stray art (offline, Pillow).

  python3 tools/audit_cat_sprites.py OUT_DIR [--no-images]

Reads android-wrapper/native/src/main/resources/pet/ (sheets, palettes.json, anims.json), writes
contact sheets to OUT_DIR (never commit them), and OUT_DIR/audit.json with every number; prints a
summary. See docs/specs/PET_SPRITE_CONSISTENCY_AUDIT.md for the method and the findings.
Not covered: rig/*.bin. The rig draws no pixels of its own: it cuts parts out of these same side
sheets at run time (RigCompose.compose(src, ...)), so every index it moves is audited here.
"""

import json
import os
import sys

import numpy as np

import audit_cat_sprites_checks as C
import audit_cat_sprites_render as R
from audit_cat_sprites_lib import (COATS, DEPTH_KINDS, KINDS, SIDE_KINDS, STILL_CLIPS, clips_for,
                                   frames_of, l_table, load_anims, load_palettes, load_sheet)


def structure_checks(side, depth):
    """(f) cross-stage plausibility, (h) weak variants vs base rows, (i) duplicate/odd frames."""
    f_rows, h_rows, i_rows = [], [], []
    sheets = {k: load_sheet(k, "gray") for k in SIDE_KINDS}
    for clip in side:
        prev = None
        for kind in SIDE_KINDS:
            fr = frames_of(sheets[kind], clip)
            st = [s for s in C.silhouette_stats(fr) if s]
            if not st:
                continue
            w = float(np.median([s["x1"] - s["x0"] + 1 for s in st]))
            h = float(np.median([s["y1"] - s["y0"] + 1 for s in st]))
            low = [s["y1"] for s in st]
            floor = int(np.bincount(low).argmax())
            below = [i for i, s in enumerate(C.silhouette_stats(fr)) if s and s["y1"] > floor]
            cy = [s["cy"] for s in st]
            cx = [s["cx"] for s in st]
            jump = max(max(abs(cx[i] - cx[(i + 1) % len(cx)]), abs(cy[i] - cy[(i + 1) % len(cy)]))
                       for i in range(len(cx)))
            if prev and (w < prev[0] - 0.5 or h < prev[1] - 0.5):
                f_rows.append(dict(check="f-shrinks", kind=kind, clip=clip.name,
                                   what=f"median box {w:.0f}x{h:.0f} is smaller than the previous "
                                        f"stage's {prev[0]:.0f}x{prev[1]:.0f}"))
            if below and clip.name in STILL_CLIPS:
                f_rows.append(dict(check="f-below-floor", kind=kind, clip=clip.name,
                                   frames=below, what=f"frames reach below the row's floor {floor}"))
            if jump > 3.0 and clip.name in STILL_CLIPS:
                f_rows.append(dict(check="f-centre-jump", kind=kind, clip=clip.name,
                                   what=f"centre of mass moves {jump:.1f}px between frames"))
            prev = (w, h)
            for i in range(len(fr)):
                j = (i + 1) % len(fr)
                if len(fr) > 2 and i < len(fr) - 1 and (fr[i] == fr[j]).all():
                    i_rows.append(dict(check="i-duplicate-frame", kind=kind, clip=clip.name,
                                       frame=i, what=f"frame {i} is identical to frame {j}"))
            if clip.name in STILL_CLIPS and clip.name not in ("sleep", "sleep_w"):
                med = np.median(np.stack([(x > 0) for x in fr]), axis=0)
                for i, x in enumerate(fr):
                    d = int(((x > 0) != med).sum())
                    if d > 60:
                        i_rows.append(dict(check="i-still-differs", kind=kind, clip=clip.name,
                                           frame=i, what=f"silhouette differs from the row's "
                                                         f"median in {d} px"))
    for kind in SIDE_KINDS:
        for base in ("idle_sit", "walk", "loaf", "sleep"):
            a = next(c for c in side if c.name == base)
            b = next(c for c in side if c.name == base + "_w")
            sa = [s for s in C.silhouette_stats(frames_of(sheets[kind], a)) if s]
            sb = [s for s in C.silhouette_stats(frames_of(sheets[kind], b)) if s]
            if not sa or not sb:
                continue
            ar, br = np.mean([s["area"] for s in sa]), np.mean([s["area"] for s in sb])
            h_rows.append(dict(check="h-weak-vs-base", kind=kind, clip=base + "_w",
                               area_ratio=float(br / ar),
                               dcx=float(np.mean([s["cx"] for s in sb]) - np.mean([s["cx"] for s in sa])),
                               dh=float(np.mean([s["y1"] - s["y0"] for s in sb])
                                        - np.mean([s["y1"] - s["y0"] for s in sa]))))
    return f_rows, h_rows, i_rows


def main(argv):
    if len(argv) < 2:
        print(__doc__)
        return 2
    out = argv[1]
    os.makedirs(out, exist_ok=True)
    side, depth = load_anims()
    pals = load_palettes()
    per_clip = {}
    result = dict(sheets=0, frames=0, palette=[], a=[], pixels=[], flips=[], cross_coat=[])
    for coat in COATS:
        result["palette"] += C.palette_rows(coat, pals[coat])
    for coat in COATS:
        ltab = l_table(pals[coat])
        for kind in KINDS:
            sheet = load_sheet(kind, coat)
            result["sheets"] += 1
            result["a"] += C.check_palette(kind, coat, sheet, pals[coat])
            for clip in clips_for(kind, side, depth):
                fr = frames_of(sheet, clip)
                result["frames"] += len(fr)
                per_clip[(kind, clip.name)] = len(fr)
                result["pixels"] += C.check_pixels(kind, coat, clip, fr, ltab)
                result["flips"].append(C.flip_stats(kind, coat, clip, fr, ltab))
    for kind in KINDS:
        sheets = {c: load_sheet(kind, c) for c in COATS}
        for clip in clips_for(kind, side, depth):
            result["cross_coat"] += C.check_cross_coat(kind, clip, sheets)
    result["pixel_rows_all"] = len(result["pixels"])
    result["pixels"] = C.flag_outliers(result["pixels"], per_clip)
    result["f"], result["h"], result["i"] = structure_checks(side, depth)
    with open(os.path.join(out, "audit.json"), "w") as f:
        json.dump(result, f, default=lambda o: o.item() if hasattr(o, "item") else str(o))
    print(f"sheets {result['sheets']}  frames {result['frames']}")
    if "--no-images" not in argv:
        names = R.all_images(side, depth, pals, out)
        print(f"{len(names)} images in {out}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
