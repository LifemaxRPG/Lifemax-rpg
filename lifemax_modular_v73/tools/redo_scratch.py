"""Scratch family: a seated cat reaching up at the wall and swiping its paws down, alternating."""
import sys; sys.path.insert(0, 'tools')
from sprite_lib import *

def disc(mask, x, y, r):
    for yy in range(int(y - r - 1), int(y + r + 2)):
        for xx in range(int(x - r - 1), int(x + r + 2)):
            if 0 <= yy < mask.shape[0] and 0 <= xx < mask.shape[1] and (xx - x) ** 2 + (yy - y) ** 2 <= r * r: mask[yy, xx] = True

def arm(fr, S, E, T, claws=True, shade=False):
    """shoulder S -> elbow E -> paw T (forearm runs along the wall)."""
    mask = np.zeros(fr.shape, bool); tpos = np.full(fr.shape, 9.0); n = 60
    for i in range(n + 1):
        t = i / n
        if t < .5: u = t / .5; x = S[0] + (E[0] - S[0]) * u; y = S[1] + (E[1] - S[1]) * u
        else: u = (t - .5) / .5; x = E[0] + (T[0] - E[0]) * u; y = E[1] + (T[1] - E[1]) * u
        r = 1.9 - 0.15 * t + (0.6 if t > .88 else 0)
        m0 = mask.copy(); disc(mask, x, y, r)
        tpos[mask & ~m0] = t
    out = fr.copy()
    ring = np.zeros_like(mask)
    ring[1:] |= mask[:-1]; ring[:-1] |= mask[1:]; ring[:, 1:] |= mask[:, :-1]; ring[:, :-1] |= mask[:, 1:]
    out[ring & ~mask] = OUT
    for y, x in zip(*np.nonzero(mask)):
        t = tpos[y, x]; right = x >= np.nonzero(mask[y])[0].max()
        v = 30 if (shade or right) else 40
        if t > .86: v = 50 if not shade else 40
        elif .55 < t < .8 and (y // 2) % 2 == 0 and not right: v = 70
        out[y, x] = v
    if claws:
        tx, ty = int(round(T[0])), int(round(T[1]))
        for dy in (-1, 0, 1): out[ty + dy, tx + 3] = 190
        out[ty, tx + 2] = 130
    return out

def frames(base, kind):
    """kind: loop(8) / in(3) / out(3). Wall is x>=63."""
    res = []
    S_near, S_far = (46, 27), (42, 28)
    def pose(py_near, py_far, reach):
        fr = base.copy()
        fr = arm(fr, S_far, (53, 33), (59 + reach, py_far), shade=True)   # far paw: drawn first, darker
        fr = arm(fr, S_near, (55, 31), (61 + reach, py_near))
        return fr
    if kind == 'loop':
        for k in range(8):
            ph = k / 8
            def y(p): return 13 + 17 * ((p % 1.0) ** 1.5)   # high -> sweeps down, then snaps back up
            res.append(pose(y(ph), y(ph + .5), 1 if ph % .5 < .35 else 0))
    else:
        order = [0, 1, 2] if kind == 'in' else [2, 1, 0]
        for i in order:
            u = (i + 1) / 3
            ynear = 40 - 27 * u; yfar = 40 - 22 * u
            res.append(pose(ynear, yfar, u > .6))
    return res

if __name__ == '__main__' and len(sys.argv) < 2:
    s, m, A = load_block('petAssets.html', 'petAssets')
    out = []
    for src in [A['coats']['ginger'], A['coats']['tuxedo']]:
        a = dec(src); base = frame(a, 0, 0); f = frames(base, 'loop'); out += [f[0], f[2], f[4], f[6]]
    preview([x[:, 8:72] for x in out], scale=5, path='/tmp/scr_new.png')

def apply_all():
    s, m, A = load_block('petAssets.html', 'petAssets'); an = A['anims']
    for c, src in A['coats'].items():
        a = dec(src); base = frame(a, an['idle_sit']['row'], 0)
        for kind, name in (('loop', 'scratch'), ('in', 'scratch_in'), ('out', 'scratch_out')):
            fs = frames(base, kind); assert len(fs) == an[name]['n']
            for i, fr in enumerate(fs): put(a, an[name]['row'], i, fr)
        A['coats'][c] = enc(a)
    save_block('petAssets.html', 'petAssets', A); print('scratch redone for', list(A['coats']))
if len(sys.argv) > 1 and sys.argv[1] == 'apply': apply_all()
