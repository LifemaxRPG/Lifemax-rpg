"""Back-view walk: remove the stripe 'tail', draw a real tail that sways. Works on any sheet with the 72x48 frame grid."""
import sys; sys.path.insert(0, 'tools')
from sprite_lib import *

def trace_tail(fr):
    """Return {y:(l,r)} for the stripe tail pixels (outlines inclusive), traced from the top down."""
    ys = np.nonzero(fr.any(1))[0]; top = ys.min(); res = {}; prev = None
    for y in range(top, 44):
        row = fr[y]; outs = [x for x in range(2, 70) if row[x] == OUT]
        best = None
        for l in outs:
            for r in outs:
                if 2 <= r - l <= 5 and not any(row[l + 1:r] == OUT) and (row[l + 1:r] != 0).all():
                    if prev is None:
                        if 34 <= l <= 46: c = abs(l - 39)
                        else: continue
                    else:
                        if l < prev[0] - 2 or r > prev[1] + 2 or l > prev[1] or r < prev[0]: continue
                        c = abs((l + r) - (prev[0] + prev[1]))
                    if best is None or c < best[0]: best = (c, l, r)
        if best: prev = (best[1], best[2]); res[y] = prev
        elif prev is not None and y > top + 12: break
    return res

def remove_tail(fr, tr):
    kill = np.zeros(fr.shape, bool)
    for y, (l, r) in tr.items(): kill[y, l:r + 1] = True
    return inpaint(fr, kill)


def bbox(fr):
    ys = np.nonzero(fr.any(1))[0]; xs = np.nonzero(fr.any(0))[0]
    return xs.min(), xs.max(), ys.min(), ys.max()

def front_head(ff):
    """Back-of-head from the front-view head: face painted out with the surrounding fur."""
    x0, x1, top, bot = bbox(ff)
    eyes = np.nonzero(np.isin(ff, (160, 170)).any(1))[0]
    mouth = np.nonzero(np.isin(ff, (200, 210)).any(1))[0]
    hb = (mouth.max() if len(mouth) else eyes.max() + 3) + 0   # cheeks end about the mouth line
    # drop everything that is not head: whiskers/stray cheek bits right of the face are removed by FACE
    h = ff.copy(); h[hb + 1:] = 0
    kill = np.isin(h, list(FACE))
    h = inpaint(h, kill)
    band = h[top + 2:top + 2 + max(6, (hb - top) // 2)]
    xs = np.nonzero(band.any(0))[0]; h[:, :xs.min()] = 0; h[:, xs.max() + 1:] = 0
    return h, top, hb

def draw_tail(fr, sway, phase):
    x0, x1, top, bot = bbox(fr); H = bot - top; s = H / 44.0; cx = (x0 + x1) / 2
    P = [(cx + 3 * s, top + .72 * H), (x1 + 5 * s, top + .74 * H), (x1 + 7 * s + sway, top + .38 * H), (x1 + 2.5 * s + 1.6 * sway, top + .12 * H)]
    N = 90; pts = []
    for i in range(N + 1):
        t = i / N; u = 1 - t
        x = u**3 * P[0][0] + 3 * u * u * t * P[1][0] + 3 * u * t * t * P[2][0] + t**3 * P[3][0]
        y = u**3 * P[0][1] + 3 * u * u * t * P[1][1] + 3 * u * t * t * P[2][1] + t**3 * P[3][1]
        pts.append((x, y, t))
    mask = np.zeros(fr.shape, bool); tpos = np.full(fr.shape, 9.0); side = np.zeros(fr.shape)
    for x, y, t in pts:
        r = (2.1 - 0.7 * t) * s ** 0.6 + 0.1
        for yy in range(int(y - r - 1), int(y + r + 2)):
            for xx in range(int(x - r - 1), int(x + r + 2)):
                if 0 <= yy < fr.shape[0] and 0 <= xx < fr.shape[1] and (xx - x) ** 2 + (yy - y) ** 2 <= r * r:
                    mask[yy, xx] = True
                    if t < tpos[yy, xx]: tpos[yy, xx] = t
                    side[yy, xx] = xx - x
    out = fr.copy()
    ring = np.zeros_like(mask)
    ring[1:] |= mask[:-1]; ring[:-1] |= mask[1:]; ring[:, 1:] |= mask[:, :-1]; ring[:, :-1] |= mask[:, 1:]
    out[ring & ~mask] = OUT
    for y, x in zip(*np.nonzero(mask)):
        t = tpos[y, x]; band = int(t * 90 / (3.2 * max(s, .6))) % 3 == 2
        v = 30 if side[y, x] > 0.7 else 40
        if band: v = 70 if v == 40 else 60
        if t > .93: v = 20
        out[y, x] = v
    return out

def new_back_frame(back, front, f):
    ff = front
    fr = remove_tail(back, trace_tail(back))
    bx0, bx1, btop, bbot = bbox(back)
    head, ftop, hb = front_head(ff)
    hh = hb - ftop + 1
    # the hips start where the old neck line is: keep rows from there down, paste the head above it
    cut = btop + hh
    out = fr.copy(); out[:cut] = 0
    fx0, fx1, _, _ = bbox(head)
    dx = int(round((bx0 + bx1) / 2 - (fx0 + fx1) / 2))
    seg = head[ftop:hb + 1]
    for y in range(seg.shape[0]):
        for x in range(seg.shape[1]):
            if seg[y, x]:
                xx = x + dx; yy = btop + y
                if 0 <= xx < 72: out[yy, xx] = seg[y, x]
    # soft join: keep the old hip pixels below, but where head bottom row meets nothing, nothing to do
    return out

def redo_sheet(dep):
    dep = dep.copy()
    for f in range(6):
        nb = new_back_frame(frame(dep, 1, f), frame(dep, 0, f), f)
        put(dep, 1, f, draw_tail(nb, 2.2 * np.sin(2 * np.pi * f / 6), f))
    return dep

if __name__ == '__main__' and len(sys.argv) < 2:
    s, m, A = load_block('petAssets.html', 'petAssets')
    D = A['depth']; out = []
    for src in [D['coats']['ginger'], D['coats']['tuxedo'], D['stages'][0]['coats']['ginger'], D['stages'][2]['coats']['gray'], D['stages'][4]['coats']['black']]:
        d = redo_sheet(dec(src)); out += [frame(d, 1, 0)[:, 12:64], frame(d, 1, 2)[:, 12:64]]
    preview(out, scale=6, path='/tmp/new_back2.png')

def apply_all():
    s, m, A = load_block('petAssets.html', 'petAssets'); D = A['depth']
    for c in D['coats']: D['coats'][c] = enc(redo_sheet(dec(D['coats'][c])))
    for st in D['stages']:
        for c in st['coats']: st['coats'][c] = enc(redo_sheet(dec(st['coats'][c])))
    save_block('petAssets.html', 'petAssets', A)
    print('walk_back redone on', len(D['coats']), 'coats +', len(D['stages']), 'stages')
if len(sys.argv) > 1 and sys.argv[1] == 'apply': apply_all()
