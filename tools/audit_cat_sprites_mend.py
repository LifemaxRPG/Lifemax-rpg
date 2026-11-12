"""Python twin of the native loader mends (SheetMend.kt: OutlineGaps, RestingShade), so the audit can
print before/after numbers. Works on one clip's frames [n, 48, 72] of palette indices."""

import numpy as np

from audit_cat_sprites_lib import FH, FW, OUTLINE, is_coat
from audit_cat_sprites_checks import cell_class, local_signature


def close_gaps(frames):
    """Coat-shade pixels with a clear and an outline 4-neighbour inside the frame become outline,
    repeated until none is left (at most 4 passes)."""
    out = frames.copy()
    for _ in range(4):
        changed = False
        for f in out:
            n = [np.full(f.shape, -1) for _ in range(4)]
            n[0][1:, :] = f[:-1, :]
            n[1][:-1, :] = f[1:, :]
            n[2][:, 1:] = f[:, :-1]
            n[3][:, :-1] = f[:, 1:]
            open_ = is_coat(f) & np.any([x == 0 for x in n], 0) & np.any([x == OUTLINE for x in n], 0)
            if open_.any():
                f[open_] = OUTLINE
                changed = True
        if not changed:
            break
    return out


def freeze_shade(frames):
    """Each coat pixel takes the commonest shade (earliest frame on a tie) among the frames where
    it is coat and its 3x3 neighbourhood has the same cell classes."""
    out = frames.copy()
    n = len(frames)
    sigs = np.stack([local_signature(cell_class(f)) for f in frames])  # [n, 9, h, w]
    for y in range(FH):
        for x in range(FW):
            col = frames[:, y, x]
            if is_coat(col).sum() < 2:
                continue
            for f in range(n):
                if not is_coat(col[f]):
                    continue
                same = [g for g in range(n) if is_coat(col[g]) and (sigs[g, :, y, x] == sigs[f, :, y, x]).all()]
                vals = [int(col[g]) for g in same]
                best = max(vals, key=lambda v: (vals.count(v), -vals.index(v)))
                out[f, y, x] = best
    return out
