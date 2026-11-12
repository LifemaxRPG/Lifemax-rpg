"""Automatic checks (a)-(i) of tools/audit_cat_sprites.py. Every check returns rows (dicts) with the
sheet kind, coat, clip, frame, the pixel count and a plain description; main sorts them by
visibility (pixels x the lightness step they change, L* units)."""

import numpy as np

from audit_cat_sprites_lib import (COATS, FEATURES, FH, FW, LIT, OUTLINE, PINKS, SHADE, SIDE_KINDS,
                                   STILL_CLIPS, WHITES, frames_of, is_coat, l_table, load_sheet,
                                   neighbours4, neighbours8)

SPECK_L = 8.0  # a lone coat pixel this many L* from its neighbours is a visible speck


def cell_class(f):
    """0 clear, 1 outline, 2 coat shade, 3 anything else (eyes, mouth, whites, pink)."""
    c = np.zeros(f.shape, np.int8)
    c[f == OUTLINE] = 1
    c[is_coat(f)] = 2
    c[(f > 0) & (f != OUTLINE) & ~is_coat(f)] = 3
    return c


def local_signature(c):
    return np.stack(neighbours8(c) + [c], 0)


def pure_flips(frames):
    """Per frame pair (f, f+1 wrapping): mask of pixels that are coat in both with a different
    shade while the 3x3 cell classes around them are identical (the shade changed, nothing moved)."""
    sigs = [local_signature(cell_class(f)) for f in frames]
    n = len(frames)
    out = []
    for f in range(n):
        g = (f + 1) % n
        out.append(is_coat(frames[f]) & is_coat(frames[g]) & (frames[f] != frames[g])
                   & (sigs[f] == sigs[g]).all(0))
    return out


def flag_outliers(rows, frames_per_clip):
    """Lone pixels, buried outline and 2x2 outline blocks are the art's own texture (tail pads,
    ear edges, paw lines): ~10 per frame. Keep only frames well above their clip's median."""
    from collections import defaultdict
    groups = defaultdict(list)
    for r in rows:
        groups[(r["check"], r["kind"], r["coat"], r["clip"])].append(r)
    keep = []
    for (check, kind, coat, clip), rs in groups.items():
        if check in ("c-outline-gap", "b-stray-outline"):
            keep += rs
            continue
        n = frames_per_clip[(kind, clip)]
        counts = sorted([r["pixels"] for r in rs] + [0] * (n - len(rs)))
        med = counts[n // 2]
        keep += [r for r in rs if r["pixels"] >= med + 6 and r["pixels"] >= 1.8 * max(med, 1)]
    return keep


def check_palette(kind, coat, sheet, palette):
    rows = []
    h = np.bincount(sheet.ravel(), minlength=256)
    for idx in range(1, 256):
        if h[idx] and idx not in palette:
            rows.append(dict(check="a-undefined-index", kind=kind, coat=coat, clip="*", frame=-1,
                             pixels=int(h[idx]), score=1000.0,
                             what=f"index {idx} has no colour in the coat, drawn clear"))
        elif 0 < h[idx] <= 6:
            rows.append(dict(check="a-rare-index", kind=kind, coat=coat, clip="*", frame=-1,
                             pixels=int(h[idx]), score=1.0,
                             what=f"index {idx} used by only {int(h[idx])} pixels in the sheet"))
    return rows


def check_pixels(kind, coat, clip, frames, ltab):
    """(b) strays and (c) outline integrity for every frame of a clip."""
    rows = []
    for fi, f in enumerate(frames):
        opaque = f > 0
        n8 = neighbours8(f)
        alone = opaque & ~np.any([x == f for x in n8], axis=0)
        for name, sel, what in (
            ("coat", is_coat(f), "a lone coat-shade pixel differing from all 8 neighbours"),
            ("white-or-pink", np.isin(f, WHITES + PINKS), "a lone white/pink pixel"),
            ("outline", f == OUTLINE, "a lone outline pixel"),
        ):
            m = alone & sel
            if name == "coat":
                # only visible specks: lightness off the neighbours' mean by SPECK_L
                nb = np.stack([ltab[x] * (x > 0) for x in n8]).sum(0) / np.maximum(
                    np.stack([(x > 0) for x in n8]).sum(0), 1)
                m = m & (np.abs(ltab[f] - nb) >= SPECK_L)
                dl = np.abs(ltab[f] - nb)[m].sum()
            else:
                dl = 15.0 * m.sum()
            if m.sum():
                rows.append(dict(check=f"b-stray-{name}", kind=kind, coat=coat, clip=clip.name,
                                 frame=fi, pixels=int(m.sum()), score=float(dl), what=what,
                                 where=[tuple(map(int, p)) for p in np.argwhere(m)[:6]]))
        # out of the frame is not clear: a cat touching the frame border has no outline there
        pad = np.pad(f, 1, constant_values=255)
        n4 = [pad[0:FH, 1:FW + 1], pad[2:FH + 2, 1:FW + 1], pad[1:FH + 1, 0:FW], pad[1:FH + 1, 2:FW + 2]]
        edge = opaque & np.any([x == 0 for x in n4], axis=0)
        gap = edge & (f != OUTLINE) & ~np.isin(f, FEATURES)
        if gap.sum():
            rows.append(dict(check="c-outline-gap", kind=kind, coat=coat, clip=clip.name, frame=fi,
                             pixels=int(gap.sum()), score=float(25 * gap.sum()),
                             what="silhouette pixel that is not outline (the ring is open)",
                             where=[tuple(map(int, p)) for p in np.argwhere(gap)[:6]]))
        ol = f == OUTLINE
        p = np.pad(ol, 1)
        thick = ol & p[1 + 0:1 + FH, 2:2 + FW] & p[2:2 + FH, 1:1 + FW] & p[2:2 + FH, 2:2 + FW]
        if thick.sum():
            rows.append(dict(check="c-outline-double", kind=kind, coat=coat, clip=clip.name,
                             frame=fi, pixels=int(thick.sum()), score=float(10 * thick.sum()),
                             what="2x2 block of outline (double-thick spot)"))
        inside = ol & np.all([x > 0 for x in n8], axis=0) & (
            np.sum([x == OUTLINE for x in n8], axis=0) <= 2)
        if inside.sum():
            rows.append(dict(check="c-outline-inside", kind=kind, coat=coat, clip=clip.name,
                             frame=fi, pixels=int(inside.sum()), score=float(10 * inside.sum()),
                             what="outline pixel buried in the body with <=2 outline neighbours",
                             where=[tuple(map(int, p)) for p in np.argwhere(inside)[:6]]))
    return rows


def flip_stats(kind, coat, clip, frames, ltab):
    """(d) per clip: shade flips between consecutive frames (and wrap), pure ones, and pixels
    whose shade differs anywhere in the row."""
    n = len(frames)
    pure = pure_flips(frames)
    flips, dl_pair = [], []
    for f in range(n):
        g = (f + 1) % n
        m = is_coat(frames[f]) & is_coat(frames[g]) & (frames[f] != frames[g])
        flips.append(int(m.sum()))
        dl_pair.append(float(np.abs(ltab[frames[f]] - ltab[frames[g]])[pure[f]].sum()))
    coatm = np.stack([is_coat(f) for f in frames])
    vals = np.where(coatm, frames, 0)
    anywhere = 0
    for y in range(FH):
        for x in range(FW):
            v = vals[:, y, x][coatm[:, y, x]]
            if len(v) > 1 and (v != v[0]).any():
                anywhere += 1
    pure_max = max(int(p.sum()) for p in pure)
    worst = int(np.argmax([int(p.sum()) for p in pure]))
    return dict(check="d-shade-flips", kind=kind, coat=coat, clip=clip.name, frames=n,
                flips_total=sum(flips), flips_max=max(flips), pure_total=int(sum(p.sum() for p in pure)),
                pure_max=pure_max, worst_pair=f"{worst}->{(worst + 1) % n}", anywhere=anywhere,
                score=float(max(dl_pair)))


def silhouette_stats(frames):
    """Bounding box, centre of mass and lowest row per frame."""
    out = []
    for f in frames:
        ys, xs = np.nonzero(f)
        if len(ys) == 0:
            out.append(None)
            continue
        out.append(dict(x0=int(xs.min()), x1=int(xs.max()), y0=int(ys.min()), y1=int(ys.max()),
                        cx=float(xs.mean()), cy=float(ys.mean()), area=len(ys)))
    return out


def check_cross_coat(kind, clip, sheets):
    """(e) index-map differences of the same frame between coats, split into what they are:
    silhouette (opaque vs clear), outline, shade-only, feature, white/pink."""
    rows = []
    base = frames_of(sheets["gray"], clip)
    for coat in COATS:
        if coat == "gray":
            continue
        fr = frames_of(sheets[coat], clip)
        for fi in range(len(fr)):
            a, b = base[fi], fr[fi]
            diff = a != b
            if not diff.any():
                continue
            sil = (a > 0) != (b > 0)
            oline = ((a == OUTLINE) != (b == OUTLINE)) & ~sil
            white = (np.isin(a, WHITES) | np.isin(b, WHITES)) & diff & ~sil & ~oline
            feat = (np.isin(a, FEATURES) | np.isin(b, FEATURES)) & diff & ~sil & ~oline & ~white
            shade = diff & ~sil & ~oline & ~white & ~feat
            rows.append(dict(check="e-cross-coat", kind=kind, coat=coat, clip=clip.name, frame=fi,
                             pixels=int(diff.sum()), score=float(sil.sum() * 20 + oline.sum() * 15),
                             silhouette=int(sil.sum()), outline=int(oline.sum()),
                             whites=int(white.sum()), features=int(feat.sum()),
                             shade=int(shade.sum()), vs="gray"))
    return rows


def palette_rows(coat, palette):
    """(g) ramp monotonicity and adjacent-level contrast (CIE L* and CIE76 delta E)."""
    from audit_cat_sprites_lib import delta_e, lab_l
    rows = []
    for name, ramp in (("lit", LIT), ("shade", SHADE), ("white", WHITES), ("pink", PINKS)):
        ls = [lab_l(palette[i]) for i in ramp if i in palette]
        ids = [i for i in ramp if i in palette]
        for j in range(len(ids) - 1):
            rows.append(dict(coat=coat, ramp=name, a=ids[j], b=ids[j + 1], dl=ls[j + 1] - ls[j],
                             de=delta_e(palette[ids[j]], palette[ids[j + 1]]),
                             monotone=bool(ls[j + 1] > ls[j])))
    return rows
